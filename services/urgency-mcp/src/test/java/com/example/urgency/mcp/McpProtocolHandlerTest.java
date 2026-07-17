package com.example.urgency.mcp;

import com.example.urgency.service.UrgencyScorer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McpProtocolHandlerTest {

    private final McpJson json = new McpJson();

    @Test
    void rejectsMissingProtocolVersion() {
        ObjectNode response = handler(new StubScorer(5.0)).handle(
                McpRequestHeaders.of(null, McpMethod.TOOLS_LIST.methodName(), null),
                requestBody(McpMethod.TOOLS_LIST));

        assertError(response, "Missing MCP-Protocol-Version header");
    }

    @Test
    void rejectsMismatchedMethodHeader() {
        ObjectNode response = handler(new StubScorer(5.0)).handle(
                McpRequestHeaders.of(McpRequestValidator.PROTOCOL_VERSION, McpMethod.PING.methodName(), null),
                requestBody(McpMethod.TOOLS_LIST));

        assertError(response, "Mcp-Method does not match JSON-RPC method");
    }

    @Test
    void returnsDiscoveryDocument() {
        ObjectNode response = handler(new StubScorer(5.0)).handle(
                headers(McpMethod.SERVER_DISCOVER),
                requestBody(McpMethod.SERVER_DISCOVER));

        JsonNode result = response.get("result");
        assertEquals(McpRequestValidator.PROTOCOL_VERSION, result.get("protocolVersion").asText());
        assertEquals(McpDiscoveryDocument.SERVER_NAME, result.get("serverInfo").get("name").asText());
    }

    @Test
    void returnsToolsList() {
        ObjectNode response = handler(new StubScorer(5.0)).handle(
                headers(McpMethod.TOOLS_LIST),
                requestBody(McpMethod.TOOLS_LIST));

        JsonNode tool = response.get("result").get("tools").get(0);
        assertEquals(McpTool.GET_URGENCY.toolName(), tool.get("name").asText());
        assertEquals(McpToolCatalog.CACHE_TTL_MS, tool.get("ttlMs").longValue());
        assertEquals(McpToolCatalog.CACHE_SCOPE, tool.get("cacheScope").asText());
    }

    @Test
    void callsUrgencyTool() {
        StubScorer scorer = new StubScorer(8.5);
        ObjectNode response = handler(scorer).handle(
                McpRequestHeaders.of(McpRequestValidator.PROTOCOL_VERSION, McpMethod.TOOLS_CALL.methodName(),
                        McpTool.GET_URGENCY.toolName()),
                toolsCallBody("patient cannot access medication"));

        JsonNode result = response.get("result");
        assertEquals("patient cannot access medication", scorer.complaint);
        assertEquals("8.5", result.get("content").get(0).get("text").asText());
        assertEquals(8.5, result.get("structuredContent").get("score").doubleValue());
        assertFalse(result.get("isError").asBoolean());
    }

    @Test
    void rejectsMissingToolNameHeader() {
        ObjectNode response = handler(new StubScorer(5.0)).handle(
                McpRequestHeaders.of(McpRequestValidator.PROTOCOL_VERSION, McpMethod.TOOLS_CALL.methodName(), null),
                toolsCallBody("patient cannot access medication"));

        assertError(response, "Missing Mcp-Name header");
    }

    private McpProtocolHandler handler(UrgencyScorer scorer) {
        return new McpProtocolHandler(() -> scorer);
    }

    private static McpRequestHeaders headers(McpMethod method) {
        return McpRequestHeaders.of(McpRequestValidator.PROTOCOL_VERSION, method.methodName(), null);
    }

    private ObjectNode requestBody(McpMethod method) {
        return json.objectNode()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", method.methodName());
    }

    private ObjectNode toolsCallBody(String phrase) {
        ObjectNode arguments = json.objectNode().put("phrase", phrase);
        ObjectNode params = json.objectNode()
                .put("name", McpTool.GET_URGENCY.toolName())
                .set("arguments", arguments);
        return json.objectNode()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", McpMethod.TOOLS_CALL.methodName())
                .set("params", params);
    }

    private static void assertError(ObjectNode response, String message) {
        assertEquals(message, response.get("error").get("message").asText());
    }

    private static final class StubScorer implements UrgencyScorer {
        private final double score;
        private String complaint;

        private StubScorer(double score) {
            this.score = score;
        }

        @Override
        public double score(String complaint) {
            this.complaint = complaint;
            return score;
        }
    }
}
