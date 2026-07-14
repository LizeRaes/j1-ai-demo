package com.example.urgency.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class McpJson {

    private final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode objectNode() {
        return mapper.createObjectNode();
    }

    public ObjectNode readObject(InputStream inputStream) {
        try {
            return (ObjectNode) mapper.readTree(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String write(ObjectNode object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
