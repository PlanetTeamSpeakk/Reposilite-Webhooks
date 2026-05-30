package com.ptsmods.repwh;

import com.reposilite.console.api.CommandsSetupEvent;
import com.reposilite.journalist.Logger;
import com.reposilite.maven.api.DeployEvent;
import com.reposilite.maven.api.PreResolveEvent;
import com.reposilite.maven.api.ResolvedFileDataEvent;
import com.reposilite.maven.api.ResolvedFileEvent;
import com.reposilite.plugin.api.*;
import com.reposilite.web.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Plugin(name = "Webhooks", version = "1.0.0")
public class ReposiliteWebhooksPlugin extends ReposilitePlugin {
    @Override
    public @Nullable Facade initialize() {
        Logger logger = getLogger();

        // All events we'll be listening for
        List<Class<? extends Event>> events = List.of(
                HttpServerStartedEvent.class,
                HttpServerConfigurationEvent.class,
                HttpServerInitializationEvent.class,
                HttpServerStoppedEvent.class,
                CommandsSetupEvent.class,
                DeployEvent.class,
                PreResolveEvent.class,
                ReposiliteDisposeEvent.class,
                ReposiliteInitializeEvent.class,
                ReposiliteStartedEvent.class,
                ReposilitePostInitializeEvent.class,
                ResolvedFileDataEvent.class,
                ResolvedFileEvent.class,
                RoutingSetupEvent.class
        );
        events.forEach(c -> extensions().registerEvent(c, this::handleEvent));

        return null;
    }

    private void handleEvent(@NotNull Event event) {
        // TODO
    }
}
