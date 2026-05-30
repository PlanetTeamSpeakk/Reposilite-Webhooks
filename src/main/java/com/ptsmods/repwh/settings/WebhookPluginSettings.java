package com.ptsmods.repwh.settings;

import com.reposilite.configuration.shared.api.Doc;
import com.reposilite.configuration.shared.api.Min;
import com.reposilite.configuration.shared.api.SharedSettings;
import io.javalin.openapi.JsonSchema;

import java.util.ArrayList;
import java.util.List;

// Settings for this plugin

@JsonSchema
@Doc(title = "Webhooks", description = "Manage where and when to send webhook pushes.")
public class WebhookPluginSettings implements SharedSettings {
    private boolean enabled = true;
    @Min(min = 1)
    private int maxConcurrentDeliveries = 4;
    private boolean logFailures = true;

    private List<WebhookSettings> webhooks = new ArrayList<>();

    @Doc(title = "Enabled", description = "Whether to send any webhooks at all.")
    public boolean isEnabled() {
        return enabled;
    }

    @Doc(title = "Max Concurrent Deliveries", description = "Maximum number of concurrent push requests allowed at a time.")
    public int getMaxConcurrentDeliveries() {
        return maxConcurrentDeliveries;
    }

    @Doc(title = "Log Failures", description = "Whether to print failures to the log. May print a lot of messages on busy instances.")
    public boolean isLogFailures() {
        return logFailures;
    }

    @Doc(title = "Configured Webhooks", description = "Individual webhook configurations.")
    public List<WebhookSettings> getWebhooks() {
        return webhooks;
    }
}
