package com.example.urgency.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class McpRequestValidator {

    public static final String PROTOCOL_VERSION = "2026-07-28";

    private final McpJsonRpcResponses responses;

    McpRequestValidator(McpJsonRpcResponses responses) {
        this.responses = responses;
    }

    McpValidationResult validate(McpRequestHeaders headers, ObjectNode body) {
        JsonNode id = body.get("id");
        if (headers.protocolVersion().isEmpty()) {
            return McpValidationResult.failed(responses.error(id, -32000, "Missing MCP-Protocol-Version header"));
        }
        if (!PROTOCOL_VERSION.equals(headers.protocolVersion().get())) {
            return McpValidationResult.failed(responses.error(id, -32000, "Unsupported MCP protocol version"));
        }
        if (headers.method().isEmpty()) {
            return McpValidationResult.failed(responses.error(id, -32000, "Missing Mcp-Method header"));
        }

        String bodyMethod = stringMember(body, "method");
        if (bodyMethod == null) {
            return McpValidationResult.failed(responses.error(id, -32600, "Missing JSON-RPC method"));
        }
        if (!headers.method().get().equals(bodyMethod)) {
            return McpValidationResult.failed(responses.error(id, -32000, "Mcp-Method does not match JSON-RPC method"));
        }

        return McpMethod.find(bodyMethod)
                .map(method -> validateToolName(headers, body, method))
                .orElseGet(() -> McpValidationResult.failed(responses.error(id, -32601, "Unsupported JSON-RPC method")));
    }

    private McpValidationResult validateToolName(McpRequestHeaders headers, ObjectNode body, McpMethod method) {
        if (!method.requiresToolName()) {
            return McpValidationResult.valid(method);
        }
        JsonNode id = body.get("id");
        if (headers.toolName().isEmpty()) {
            return McpValidationResult.failed(responses.error(id, -32000, "Missing Mcp-Name header"));
        }

        JsonNode params = body.get("params");
        String toolName = params == null ? null : stringMember(params, "name");
        if (!headers.toolName().get().equals(toolName)) {
            return McpValidationResult.failed(responses.error(id, -32000, "Mcp-Name does not match requested tool name"));
        }
        if (!McpTool.GET_URGENCY.toolName().equals(toolName)) {
            return McpValidationResult.failed(responses.error(id, -32601, "Unsupported tool name"));
        }
        return McpValidationResult.valid(method);
    }

    static String stringMember(JsonNode object, String name) {
        JsonNode value = object.get(name);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
