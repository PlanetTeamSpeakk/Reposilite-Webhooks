package com.ptsmods.repwh.settings.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.reposilite.maven.Repository;
import com.reposilite.maven.api.DeployEvent;
import com.reposilite.maven.api.PreResolveEvent;
import com.reposilite.maven.api.ResolvedFileDataEvent;
import com.reposilite.maven.api.ResolvedFileEvent;
import com.reposilite.plugin.Extensions;
import com.reposilite.plugin.api.*;
import com.reposilite.shared.ErrorResponse;
import com.reposilite.storage.api.DocumentInfo;
import com.reposilite.storage.api.Location;
import com.reposilite.token.AccessTokenIdentifier;
import com.reposilite.web.api.*;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import panda.std.Result;

import java.io.InputStream;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum EventType {

    // -------------------------------------------------------------------------
    // HTTP / lifecycle — no meaningful external payload; blank serializer used.
    // These events carry Javalin-internal objects (JavalinConfig) or the
    // Reposilite instance itself, neither of which is safe or useful to expose.
    // -------------------------------------------------------------------------

    SERVER_STARTED(HttpServerStartedEvent.class),
    SERVER_CONFIG(HttpServerConfigurationEvent.class),
    SERVER_INIT(HttpServerInitializationEvent.class),
    SERVER_STOPPED(HttpServerStoppedEvent.class),
    REPOSILITE_INIT(ReposiliteInitializeEvent.class),
    REPOSILITE_POST_INIT(ReposilitePostInitializeEvent.class),
    REPOSILITE_STARTED(ReposiliteStartedEvent.class),
    REPOSILITE_DISPOSE(ReposiliteDisposeEvent.class),

    // -------------------------------------------------------------------------
    // Maven artifact events — carry repository + GAV context.
    // Paths are expressed as /{repository}/{gav}, matching the public URL.
    // -------------------------------------------------------------------------

    /**
     * Fired after a file is successfully written to a repository.
     * {@code by} is the name of the access token that performed the deployment.
     *
     * <pre>{@code
     * {
     *   "repository":           "releases",
     *   "repositoryVisibility": "PUBLIC",
     *   "path":                 "/releases/com/example/lib/1.0/lib-1.0.jar",
     *   "by":                   "ci-bot"
     * }
     * }</pre>
     */
    DEPLOY(new EventHandler<>(DeployEvent.class, event -> {
        JsonObject obj = new JsonObject();
        obj.addProperty("repository",           event.getRepository().getName());
        obj.addProperty("repositoryVisibility", event.getRepository().getVisibility().name());
        obj.addProperty("path",                 externalPath(event.getRepository(), event.getGav()));
        obj.addProperty("by",                   event.getBy());
        return obj;
    })),

    /**
     * Fired before a file lookup is resolved. No result is available yet.
     * Useful for logging access attempts or triggering pre-download hooks.
     *
     * <pre>{@code
     * {
     *   "repository":           "releases",
     *   "repositoryVisibility": "PUBLIC",
     *   "path":                 "/releases/com/example/lib/1.0/lib-1.0.jar",
     *   "authenticated":        true,
     *   "tokenType":            "PERSISTENT"
     * }
     * }</pre>
     */
    PRE_RESOLVE(new EventHandler<>(PreResolveEvent.class, event ->
        artifactBase(event.getRepository(), event.getGav(), event.getAccessToken())
    )),

    /**
     * Fired after a file lookup is resolved, including file metadata on success.
     * {@code contentLength} is omitted when the storage provider does not report it.
     *
     * <pre>{@code
     * // success
     * {
     *   "repository": "releases", "repositoryVisibility": "PUBLIC",
     *   "path": "/releases/com/example/lib/1.0/lib-1.0.jar",
     *   "authenticated": true, "tokenType": "PERSISTENT",
     *   "success": true,
     *   "contentType": "application/java-archive",
     *   "contentLength": 45312
     * }
     * // failure
     * {
     *   ..., "success": false, "errorStatus": 404, "errorMessage": "File not found"
     * }
     * }</pre>
     */
    RESOLVED_FILE(new EventHandler<>(ResolvedFileEvent.class, event -> {
        JsonObject obj = artifactBase(event.getRepository(), event.getGav(), event.getAccessToken());
        Result<kotlin.Pair<DocumentInfo, InputStream>, ErrorResponse> result = event.getResult();
        obj.addProperty("success", result.isOk());
        if (result.isOk()) {
            DocumentInfo info = result.get().getFirst();
            obj.addProperty("contentType", info.getContentType().getMimeType());
            long contentLength = info.getContentLength();
            if (contentLength >= 0) {
                obj.addProperty("contentLength", contentLength);
            }
        } else {
            appendError(obj, result.getError());
        }
        return obj;
    })),

    /**
     * Fired after only the raw data stream of a file has been resolved (no metadata).
     * Carries success/failure but no content-type or length on success, since
     * {@link ResolvedFileDataEvent} does not include {@link DocumentInfo}.
     *
     * <pre>{@code
     * // success
     * {
     *   "repository": "releases", "repositoryVisibility": "PUBLIC",
     *   "path": "/releases/com/example/lib/1.0/lib-1.0.jar",
     *   "authenticated": true, "tokenType": "PERSISTENT",
     *   "success": true
     * }
     * // failure
     * {
     *   ..., "success": false, "errorStatus": 401, "errorMessage": "Unauthorized"
     * }
     * }</pre>
     */
    RESOLVED_FILE_DATA(new EventHandler<>(ResolvedFileDataEvent.class, event -> {
        JsonObject obj = artifactBase(event.getRepository(), event.getGav(), event.getAccessToken());
        Result<InputStream, ErrorResponse> result = event.getResult();
        obj.addProperty("success", result.isOk());
        if (result.isErr()) {
            appendError(obj, result.getError());
        }
        return obj;
    }));

    // =========================================================================

    @Getter
    private final EventHandler<? extends Event> handler;

    EventType(Class<? extends Event> eventClass) {
        this(EventHandler.blank(eventClass));
    }

    EventType(EventHandler<? extends Event> handler) {
        this.handler = handler;
    }

    // -------------------------------------------------------------------------
    // Static helpers (called from lambda bodies — safe, no forward-ref issue)
    // -------------------------------------------------------------------------

    /**
     * Returns the public-facing URL path for an artifact: /{repository}/{gav}.
     */
    private static String externalPath(Repository repository, Location gav) {
        return "/" + repository.getName() + "/" + gav;
    }

    /**
     * Builds the JSON fields shared by all artifact lookup events:
     * repository name and visibility, public path, and authentication context.
     * The access token identifier is intentionally not included — only its type
     * is exposed, since the internal integer ID is meaningless to receivers.
     */
    private static JsonObject artifactBase(Repository repository, Location gav,
                                           @Nullable AccessTokenIdentifier accessToken) {
        JsonObject obj = new JsonObject();
        obj.addProperty("repository",           repository.getName());
        obj.addProperty("repositoryVisibility", repository.getVisibility().name());
        obj.addProperty("path",                 externalPath(repository, gav));
        obj.addProperty("authenticated",        accessToken != null);
        if (accessToken != null) {
            obj.addProperty("tokenType", accessToken.getType().name());
        }
        return obj;
    }

    /**
     * Appends {@code errorStatus} and {@code errorMessage} fields to an existing object.
     */
    private static void appendError(JsonObject obj, ErrorResponse err) {
        obj.addProperty("errorStatus",  err.getStatus());
        obj.addProperty("errorMessage", err.getMessage());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** All known events — use for display/schema purposes. */
    public static Set<EventType> getAll() {
        return Stream.of(EventType.values()).collect(Collectors.toSet());
    }

    /**
     * Sensible default set for new webhooks: only the artifact-level events that
     * carry meaningful repository/GAV context. Infrastructure and lifecycle events
     * (server init, config, dispose, etc.) are excluded because they fire at startup
     * with Javalin-internal objects that cannot be meaningfully serialised as a
     * webhook payload.
     */
    public static Set<EventType> getDefaults() {
        return Stream.of(DEPLOY, RESOLVED_FILE, RESOLVED_FILE_DATA).collect(Collectors.toSet());
    }

    public Class<? extends Event> getEventClass() {
        return handler.eventClass();
    }

    // -------------------------------------------------------------------------

    /**
     * Pairs an event class with a function that converts an instance of that
     * event to a {@link JsonElement}. Use {@link #blank} when the event carries
     * no meaningful external data.
     */
    public record EventHandler<T extends Event>(
        Class<T> eventClass,
        @NonNull Function<T, @NonNull JsonElement> serializer
    ) {
        public static <T extends Event> EventHandler<T> blank(Class<T> eventClass) {
            return new EventHandler<>(eventClass, t -> JsonNull.INSTANCE);
        }

        public void registerHandler(Extensions extensions, Consumer<JsonElement> consumer) {
            extensions.registerEvent(eventClass, e -> consumer.accept(serializer.apply(e)));
        }
    }
}
