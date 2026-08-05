package com.example.urgency.mcp;

import io.helidon.http.Status;
import io.helidon.json.JsonObject;

import java.util.Objects;

public record McpProtocolResponse(Status status, JsonObject body) {

    public static McpProtocolResponse of(Status status, JsonObject body) {
        return new McpProtocolResponse(
                Objects.requireNonNull(status, "status"),
                Objects.requireNonNull(body, "body"));
    }
}