package com.example.urgency.mcp;

import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.helidon.http.media.jsonb.JsonbSupportConfig;
import jakarta.json.bind.Jsonb;

public final class McpJson {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Jsonb jsonb = JsonbSupportConfig.create().jsonb();

    public ObjectNode objectNode() {
        return mapper.createObjectNode();
    }

    public ObjectNode readObject(InputStream inputStream) {
        return asObjectNode(jsonb.fromJson(inputStream, Object.class));
    }

    public JsonNode readTree(String text) {
        return mapper.valueToTree(jsonb.fromJson(text, Object.class));
    }

    public String write(ObjectNode object) {
        return jsonb.toJson(mapper.convertValue(object, Object.class));
    }

    private ObjectNode asObjectNode(Object value) {
        JsonNode node = mapper.valueToTree(value);
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        throw new IllegalArgumentException("JSON value must be an object");
    }
}
