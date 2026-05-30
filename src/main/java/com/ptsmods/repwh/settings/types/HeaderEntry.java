package com.ptsmods.repwh.settings.types;

import com.reposilite.configuration.shared.api.Doc;
import io.javalin.openapi.JsonSchema;

@JsonSchema
@Doc(title = "Header", description = "A custom HTTP header to include in webhook requests.")
public class HeaderEntry {
    private String key = "";
    private String value = "";

    @Doc(title = "Name", description = "The header name, e.g. 'Authorization'.")
    public String getKey() {
        return key;
    }

    @Doc(title = "Value", description = "The header value, e.g. 'Bearer token123'.")
    public String getValue() {
        return value;
    }
}
