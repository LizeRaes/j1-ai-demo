package com.example.urgency.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

record McpValidationResult(McpValidatedRequest request, ObjectNode error) {
    static McpValidationResult valid(McpMethod method) {
        return new McpValidationResult(new McpValidatedRequest(method), null);
    }

    static McpValidationResult failed(ObjectNode error) {
        return new McpValidationResult(null, error);
    }

    boolean failed() {
        return error != null;
    }
}
