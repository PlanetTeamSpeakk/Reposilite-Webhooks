package com.ptsmods.repwh.settings;

import com.ptsmods.repwh.Util;
import com.ptsmods.repwh.settings.types.BodyType;
import com.ptsmods.repwh.settings.types.EventType;
import com.ptsmods.repwh.settings.types.HeaderEntry;
import com.ptsmods.repwh.settings.types.PayloadSigningAlgorithm;
import com.reposilite.configuration.shared.api.Doc;
import com.reposilite.configuration.shared.api.Max;
import com.reposilite.configuration.shared.api.Min;
import com.reposilite.configuration.shared.api.SharedSettings;
import io.javalin.openapi.JsonSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Settings for a singular webhook

@SuppressWarnings("FieldMayBeFinal")
@JsonSchema
@Doc(title = "Webhook", description = "A single webhook to push (certain) events to.")
public class WebhookSettings implements SharedSettings {

    // --- Core ---
    private String reference = "";
    private boolean enabled = true;
    private String pushUrl = "";
    private BodyType bodyType = BodyType.JSON;

    // --- Event filtering ---
    private Set<EventType> events = new HashSet<>(EventType.getDefaults());
    private List<String> repositories = new ArrayList<>();
    private String pathFilter = "";

    // --- Security ---
    private String secret = Util.generateRandomString(64);
    private PayloadSigningAlgorithm payloadSigningAlgorithm = PayloadSigningAlgorithm.NONE;

    // --- Reliability ---
    @Min(min = 100)
    private long timeoutMs = 5000;
    @Min(min = 0)
    @Max(max = 10)
    private int retryCount = 3;
    @Min(min = 0)
    private long retryDelayMs = 1000;

    // --- Advanced ---
    private List<HeaderEntry> headers = new ArrayList<>();

    @Doc(title = "Name", description = "Name of this webhook.")
    public String getReference() {
        return reference;
    }

    @Doc(title = "Enabled", description = "Whether this webhook is active.")
    public boolean isEnabled() {
        return enabled;
    }

    @Doc(title = "Push URL", description = "The URL to POST events to.")
    public String getPushUrl() {
        return pushUrl;
    }

    @Doc(title = "Body Type", description = "Format of the request body.")
    public BodyType getBodyType() {
        return bodyType;
    }

    @Doc(title = "Events", description = "Which events trigger this webhook.")
    public Set<EventType> getEvents() {
        return events;
    }

    @Doc(title = "Repositories",
            description = "Restrict this webhook to specific repository names (e.g. 'releases'). " +
                    "Leave empty to receive events from all repositories.")
    public List<String> getRepositories() {
        return repositories;
    }

    @Doc(title = "Path Filter",
            description = "A glob pattern matched against the artifact GAV path " +
                    "(e.g. 'com/example/**' or '**.pom'). Leave empty to match all paths.")
    public String getPathFilter() {
        return pathFilter;
    }

    @Doc(title = "Secret",
            description = "Used to verify the source of each push. " +
                    "Sent as a raw header when no signing algorithm is selected, " +
                    "or used as the HMAC key when one is.")
    public String getSecret() {
        return secret;
    }

    @Doc(title = "Payload Signing Algorithm",
            description = "Signs the payload with HMAC and sends the result as " +
                    "X-Hub-Signature-256 or X-Hub-Signature-512 instead of the raw secret. " +
                    "Compatible with the adnanh/webhook tool and GitHub-style receivers.")
    public PayloadSigningAlgorithm getPayloadSigningAlgorithm() {
        return payloadSigningAlgorithm;
    }

    @Doc(title = "Timeout (ms)",
            description = "Maximum time in milliseconds to wait for the endpoint to respond " +
                    "before the request is considered failed.")
    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Doc(title = "Retry Count",
            description = "How many times to retry a failed request before giving up. " +
                    "A request is retried on network error or a non-2xx response.")
    public int getRetryCount() {
        return retryCount;
    }

    @Doc(title = "Retry Delay (ms)",
            description = "How long to wait in milliseconds between retry attempts.")
    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    @Doc(title = "Headers", description = "Extra HTTP headers to send along with each request.")
    public List<HeaderEntry> getHeaders() {
        return headers;
    }
}
