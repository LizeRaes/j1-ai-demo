package com.example.urgency.mcp;

import io.helidon.json.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class McpProtocolRequest {

    private final Map<String, String> headers;
    private final JsonObject body;

    public McpProtocolRequest(Map<String, String> headers, JsonObject body) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(headers, "headers")));
        this.body = Objects.requireNonNull(body, "body");
    }

    public JsonObject body() {
        return body;
    }

    String header(String name) {
        return headers.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
