package com.example.urgency.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.helidon.http.Status;

public record McpProtocolResponse(Status status, Map<String, Object> body) {

    public McpProtocolResponse {
        Objects.requireNonNull(status, "status");
        body = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(body, "body")));
    }
}
