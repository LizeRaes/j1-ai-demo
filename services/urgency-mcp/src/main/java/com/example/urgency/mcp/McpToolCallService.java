package com.example.urgency.mcp;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.example.urgency.service.UrgencyScorer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class McpToolCallService {

    private static final String SCORER_SUPPLIER_LABEL = "urgency scorer supplier";
    private static final String REQUEST_LOG_PREFIX = "MCP server called: getUrgency(phrase=\"";
    private static final String REQUEST_LOG_SUFFIX = "\")";
    private static final String RESPONSE_LOG_PREFIX = "MCP server returning urgency score: ";
    private static final Logger log = Logger.getLogger(McpToolCallService.class.getName());

    private final McpJson json;
    private final Supplier<UrgencyScorer> scorerSupplier;

    McpToolCallService(McpJson json, Supplier<UrgencyScorer> scorerSupplier) {
        this.json = json;
        this.scorerSupplier = Objects.requireNonNull(scorerSupplier, SCORER_SUPPLIER_LABEL);
    }

    ObjectNode callResult(ObjectNode body) {
        JsonNode params = body.get("params");
        JsonNode arguments = params == null ? null : params.get("arguments");
        String phrase = arguments == null ? null : McpRequestValidator.stringMember(arguments, "phrase");
        if (phrase == null || phrase.isBlank()) {
            return toolErrorResult("Missing required string argument: phrase");
        }

        log.info(REQUEST_LOG_PREFIX + phrase + REQUEST_LOG_SUFFIX);
        double score = scorerSupplier.get().score(phrase);
        String textScore = Double.toString(score);
        log.info(RESPONSE_LOG_PREFIX + textScore);

        ObjectNode content = json.objectNode()
                .put("type", "text")
                .put("text", textScore);
        ObjectNode result = json.objectNode();
        result.set("content", json.objectNode().arrayNode().add(content));
        result.set("structuredContent", json.objectNode().put("score", score));
        result.put("isError", false);
        return result;
    }

    private ObjectNode toolErrorResult(String message) {
        ObjectNode content = json.objectNode()
                .put("type", "text")
                .put("text", message);
        ObjectNode result = json.objectNode();
        result.set("content", json.objectNode().arrayNode().add(content));
        result.put("isError", true);
        return result;
    }
}
