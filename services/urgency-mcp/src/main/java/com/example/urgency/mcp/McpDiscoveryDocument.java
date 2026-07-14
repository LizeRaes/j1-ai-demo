package com.example.urgency.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

public final class McpDiscoveryDocument {

    public static final String SERVER_NAME = "helidon-mcp-urgency";

    private final McpJson json;

    McpDiscoveryDocument(McpJson json) {
        this.json = json;
    }

    ObjectNode result() {
        ObjectNode result = json.objectNode();
        result.put("protocolVersion", McpRequestValidator.PROTOCOL_VERSION);
        result.set("serverInfo", json.objectNode()
                .put("name", SERVER_NAME)
                .put("version", "1.0.0"));
        result.set("capabilities", json.objectNode()
                .set("tools", json.objectNode()));
        result.set("methods", json.objectNode().arrayNode()
                .add(McpMethod.SERVER_DISCOVER.methodName())
                .add(McpMethod.PING.methodName())
                .add(McpMethod.TOOLS_LIST.methodName())
                .add(McpMethod.TOOLS_CALL.methodName()));
        return result;
    }
}
