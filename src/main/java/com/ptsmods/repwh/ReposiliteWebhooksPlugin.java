package com.ptsmods.repwh;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ptsmods.repwh.settings.WebhookPluginSettings;
import com.ptsmods.repwh.settings.WebhookSettings;
import com.ptsmods.repwh.settings.types.BodyType;
import com.ptsmods.repwh.settings.types.EventType;
import com.ptsmods.repwh.settings.types.HeaderEntry;
import com.reposilite.configuration.shared.SharedConfigurationFacade;
import com.reposilite.plugin.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import panda.std.reactive.MutableReference;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Plugin(name = "webhooks",
        version = BuildVersion.VERSION,
        dependencies = {"configuration", "local-configuration", "shared-configuration"},
        settings = WebhookPluginSettings.class)
public class ReposiliteWebhooksPlugin extends ReposilitePlugin {

    /** Shared HTTP client — maintains its own connection pool. */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Daemon thread pool for delivery tasks. Unbounded so tasks never queue behind
     * each other here; the semaphore below provides the actual concurrency cap.
     */
    private final ExecutorService deliveryExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "webhooks-delivery");
        t.setDaemon(true);
        return t;
    });

    /**
     * Updated atomically when {@code maxConcurrentDeliveries} changes in settings.
     * Delivery tasks snapshot this reference before acquiring so that a settings
     * update mid-flight only affects new submissions, not already-queued ones.
     */
    private final AtomicReference<Semaphore> concurrencyLimit =
            new AtomicReference<>(new Semaphore(4));

    private MutableReference<WebhookPluginSettings> settingsRef;

    // Lifecycle

    @Override
    public @Nullable Facade initialize() {
        for (EventType eventType : EventType.values()) {
            eventType.getHandler().registerHandler(extensions(), e -> handleEvent(eventType, e));
        }
        setupSettings();
        getLogger().info("Webhooks | Plugin initialized");
        return null;
    }

    private void setupSettings() {
        // Settings class set in @Plugin annotation.
        SharedConfigurationFacade config = extensions().facade(SharedConfigurationFacade.class);
        settingsRef = config.getDomainSettings(WebhookPluginSettings.class);
        settingsRef.subscribe(this::handleSettingsUpdate);
        handleSettingsUpdate(settingsRef.get());
    }

    private void handleSettingsUpdate(WebhookPluginSettings settings) {
        concurrencyLimit.set(new Semaphore(Math.max(1, settings.getMaxConcurrentDeliveries())));
    }

    // Event dispatch

    private void handleEvent(EventType eventType, @NotNull JsonElement payload) {
        WebhookPluginSettings globalSettings = settingsRef.get();

        // Global kill switch
        if (!globalSettings.isEnabled()) return;

        boolean logFailures = globalSettings.isLogFailures();

        for (WebhookSettings webhook : globalSettings.getWebhooks()) {
            // Check if webhook is valid and listens for this event.
            if (!webhook.isEnabled() || webhook.getPushUrl().isBlank() || !webhook.getEvents().contains(eventType))
                continue;

            // Check repository and path filters. Will pass if not applicable.
            if (payload instanceof JsonObject obj &&
                    (!matchesRepositoryFilter(webhook, obj) || !matchesPathFilter(webhook, obj)))
                continue;

            // Snapshot: settings may change between submission and execution.
            final WebhookSettings wh = webhook;
            final JsonElement pl = payload;
            final boolean log = logFailures;

            deliveryExecutor.submit(() -> {
                // Capture the semaphore reference now — if settings update between
                // submission and this point we use whichever limit was current then.
                Semaphore sem = concurrencyLimit.get();
                sem.acquireUninterruptibly();
                try {
                    deliver(eventType, wh, pl, log);
                } finally {
                    sem.release();
                }
            });
        }
    }

    // Delivery

    private void deliver(EventType eventType, WebhookSettings webhook,
                         JsonElement payload, boolean logFailures) {
        URI uri;
        try {
            uri = URI.create(webhook.getPushUrl());
        } catch (IllegalArgumentException e) {
            if (logFailures) {
                getLogger().warn("Webhooks | Invalid push URL '" + webhook.getPushUrl()
                        + "' for webhook '" + Util.label(webhook) + "': " + e.getMessage());
            }
            return;
        }

        // Generate envelope fields
        String deliveryId = UUID.randomUUID().toString();
        long  timestamp = System.currentTimeMillis();

        String body = Util.buildBody(webhook.getBodyType(), eventType, payload, deliveryId, timestamp);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest request = buildRequest(uri, webhook, bodyBytes);

        String failureReason = null;
        int attempts = webhook.getRetryCount() + 1;

        for (int attempt = 0; attempt < attempts; attempt++) {
            if (attempt > 0 && webhook.getRetryDelayMs() > 0) {
                try {
                    Thread.sleep(webhook.getRetryDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try {
                HttpResponse<Void> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return; // success
                failureReason = "HTTP " + status;
            } catch (IOException e) {
                failureReason = e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // All attempts exhausted.
        if (logFailures) {
            getLogger().warn("Webhooks | Failed to deliver {} to '{}'"
                    + (attempts > 1 ? " after " + attempts + " attempts" : "") + ": {}",
                    eventType.name(), webhook.getPushUrl(), failureReason);
        }
    }

    private HttpRequest buildRequest(URI uri, WebhookSettings webhook, byte[] bodyBytes) {
        String contentType = webhook.getBodyType() == BodyType.JSON
                ? "application/json; charset=utf-8"
                : "application/x-www-form-urlencoded; charset=utf-8";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(webhook.getTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .header("Content-Type", contentType);

        applySecurityHeader(builder, webhook, bodyBytes);

        for (HeaderEntry header : webhook.getHeaders()) {
            if (!header.getKey().isBlank()) {
                builder.header(header.getKey(), header.getValue());
            }
        }

        return builder.build();
    }

    /**
     * Applies the appropriate security header based on {@code payloadSigningAlgorithm}:
     * <ul>
     *   <li>{@code NONE}: {@code X-Webhook-Secret: <raw secret>}</li>
     *   <li>{@code HMAC_SHA256}: {@code X-Hub-Signature-256: sha256=<hex>}</li>
     *   <li>{@code HMAC_SHA512}: {@code X-Hub-Signature-512: sha512=<hex>}</li>
     * </ul>
     * Nothing is added when the secret is blank.
     */
    private static void applySecurityHeader(HttpRequest.Builder builder,
                                             WebhookSettings webhook, byte[] bodyBytes) {
        String secret = webhook.getSecret();
        if (secret.isBlank()) return;

        switch (webhook.getPayloadSigningAlgorithm()) {
            case NONE ->
                builder.header("X-Webhook-Secret", secret);
            case HMAC_SHA256 ->
                builder.header("X-Hub-Signature-256",
                        "sha256=" + Util.hmac("HmacSHA256", secret, bodyBytes));
            case HMAC_SHA512 ->
                builder.header("X-Hub-Signature-512",
                        "sha512=" + Util.hmac("HmacSHA512", secret, bodyBytes));
        }
    }

    // Filters

    /**
     * Returns {@code true} if the webhook's repository list is empty (= match all),
     * or if the event's repository name appears in the list.
     */
    private static boolean matchesRepositoryFilter(WebhookSettings webhook, JsonObject payload) {
        List<String> repos = webhook.getRepositories();
        if (repos.isEmpty()) return true;
        JsonElement repoEl = payload.get("repository");
        if (repoEl == null || repoEl.isJsonNull()) return true; // no repo context — pass through
        return repos.contains(repoEl.getAsString());
    }

    /**
     * Returns {@code true} if the webhook's path filter is blank (= match all),
     * or if the GAV portion of the event path matches the glob pattern.
     *
     * <p>The payload {@code path} field is {@code /{repo}/{gav}}.  The repo prefix
     * is stripped before matching so that user patterns work naturally:
     * {@code com/example/**} matches any artifact under that group, regardless of
     * which repository it lives in.
     */
    private static boolean matchesPathFilter(WebhookSettings webhook, JsonObject payload) {
        String pattern = webhook.getPathFilter();
        if (pattern.isBlank()) return true;
        JsonElement pathEl = payload.get("path");
        if (pathEl == null || pathEl.isJsonNull()) return true; // no path context, pass through

        // "/{repo}/{gav}" → strip leading '/' and then the repo segment
        String full = pathEl.getAsString(); // "/releases/com/example/lib/1.0/lib.jar"
        String noLead = full.startsWith("/") ? full.substring(1) : full;
        int slash = noLead.indexOf('/');
        String gav = slash >= 0 ? noLead.substring(slash + 1) : noLead;

        // Match either as regex or glob.
        return webhook.isRegexFilter() ? Pattern.compile(pattern).matcher(gav).find() : Util.matchesGlob(pattern, gav);
    }
}
