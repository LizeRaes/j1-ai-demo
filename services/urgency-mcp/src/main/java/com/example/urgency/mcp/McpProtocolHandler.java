package com.example.urgency.mcp;

import java.util.Objects;
import java.util.function.Supplier;

import com.example.urgency.service.UrgencyScorer;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class McpProtocolHandler {

    private static final String SCORER_SUPPLIER_LABEL = "urgency scorer supplier";

    private final McpJson json;
    private final McpJsonRpcResponses responses;
    private final McpRequestValidator validator;
    private final McpDiscoveryDocument discoveryDocument;
    private final McpToolCatalog toolCatalog;
    private final McpToolCallService toolCallService;

    public McpProtocolHandler(Supplier<UrgencyScorer> scorerSupplier) {
        Objects.requireNonNull(scorerSupplier, SCORER_SUPPLIER_LABEL);
        this.json = new McpJson();
        this.responses = new McpJsonRpcResponses(json);
        this.validator = new McpRequestValidator(responses);
        this.discoveryDocument = new McpDiscoveryDocument(json);
        this.toolCatalog = new McpToolCatalog(json);
        this.toolCallService = new McpToolCallService(json, scorerSupplier);
    }

    public ObjectNode handle(McpRequestHeaders headers, ObjectNode body) {
        McpValidationResult validation = validator.validate(headers, body);
        if (validation.failed()) {
            return validation.error();
        }

        ObjectNode result = switch (validation.request().method()) {
            case SERVER_DISCOVER -> discoveryDocument.result();
            case PING -> json.objectNode();
            case TOOLS_LIST -> toolCatalog.listResult();
            case TOOLS_CALL -> toolCallService.callResult(body);
        };
        return responses.success(body.get("id"), result);
    }
}
