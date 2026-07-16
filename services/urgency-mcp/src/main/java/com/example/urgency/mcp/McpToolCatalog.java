package com.example.urgency.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

public final class McpToolCatalog {

    public static final long CACHE_TTL_MS = 300_000L;
    public static final String CACHE_SCOPE = "public";

    private final McpJson json;

    McpToolCatalog(McpJson json) {
        this.json = json;
    }

    ObjectNode listResult() {
        ObjectNode result = json.objectNode();
        result.set("tools", json.objectNode().arrayNode().add(toolDescriptor(McpTool.GET_URGENCY)));
        return result;
    }

    private ObjectNode toolDescriptor(McpTool tool) {
        ObjectNode phrase = json.objectNode()
                .put("type", "string")
                .put("description", tool.phraseDescription());
        ObjectNode properties = json.objectNode().set("phrase", phrase);
        ObjectNode inputSchema = json.objectNode()
                .put("$schema", "https://json-schema.org/draft/2020-12/schema")
                .put("type", "object")
                .set("properties", properties);
        inputSchema.set("required", json.objectNode().arrayNode().add("phrase"));
        inputSchema.put("additionalProperties", false);

        ObjectNode descriptor = json.objectNode()
                .put("name", tool.toolName())
                .put("description", tool.description())
                .set("inputSchema", inputSchema);
        descriptor.put("ttlMs", CACHE_TTL_MS);
        descriptor.put("cacheScope", CACHE_SCOPE);
        return descriptor;
    }
}
