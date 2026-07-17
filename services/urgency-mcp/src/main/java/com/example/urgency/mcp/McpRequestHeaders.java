package com.example.urgency.mcp;

import java.util.Optional;

public record McpRequestHeaders(Optional<String> protocolVersion, Optional<String> method, Optional<String> toolName) {
    public static McpRequestHeaders of(String protocolVersion, String method, String toolName) {
        return new McpRequestHeaders(Optional.ofNullable(protocolVersion), Optional.ofNullable(method), Optional.ofNullable(toolName));
    }
}
