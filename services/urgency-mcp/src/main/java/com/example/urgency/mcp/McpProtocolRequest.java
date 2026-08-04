package com.example.urgency.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record McpProtocolRequest(Map<String, String> headers, Map<String, Object> body) {

    public McpProtocolRequest {
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(headers, "headers")));
        body = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(body, "body")));
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
