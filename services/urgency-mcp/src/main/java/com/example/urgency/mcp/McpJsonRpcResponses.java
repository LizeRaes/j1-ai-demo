package com.example.urgency.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class McpJsonRpcResponses {

    private static final String JSON_RPC_VERSION = "2.0";

    private final McpJson json;

    McpJsonRpcResponses(McpJson json) {
        this.json = json;
    }

    ObjectNode success(JsonNode id, ObjectNode result) {
        ObjectNode response = json.objectNode()
                .put("jsonrpc", JSON_RPC_VERSION)
                .set("result", result);
        addId(response, id);
        return response;
    }

    ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = json.objectNode()
                .put("jsonrpc", JSON_RPC_VERSION)
                .set("error", json.objectNode()
                        .put("code", code)
                        .put("message", message));
        addId(response, id);
        return response;
    }

    private static void addId(ObjectNode response, JsonNode id) {
        if (id == null || id.isNull()) {
            response.putNull("id");
        } else {
            response.set("id", id.deepCopy());
        }
    }
}
