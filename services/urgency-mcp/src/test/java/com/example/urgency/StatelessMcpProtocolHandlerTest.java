package com.example.urgency;

import java.util.List;
import java.util.Map;

import com.example.urgency.mcp.McpProtocolRequest;
import com.example.urgency.mcp.McpProtocolResponse;
import com.example.urgency.mcp.StatelessMcpProtocolHandler;

import io.helidon.http.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatelessMcpProtocolHandlerTest {

    @Test
    void discoversServerCapabilitiesWithCacheHints() {
        McpProtocolResponse response = handler().handle(request("server/discover"));

        assertEquals(Status.OK_200, response.status());
        Map<String, Object> result = result(response);
        assertEquals(List.of(StatelessMcpProtocolHandler.PROTOCOL_VERSION), result.get("protocolVersions"));
        assertEquals(300_000, result.get("ttlMs"));
        assertEquals("public", result.get("cacheScope"));
        assertTrue(result.containsKey("capabilities"));
        assertTrue(result.containsKey("serverInfo"));
    }

    @Test
    void listsGetUrgencyToolWithJsonSchema202012() {
        McpProtocolResponse response = handler().handle(request("tools/list"));

        Map<String, Object> result = result(response);
        assertEquals(300_000, result.get("ttlMs"));
        assertEquals("public", result.get("cacheScope"));
        Map<String, Object> tool = firstTool(result);
        assertEquals("getUrgency", tool.get("name"));
        Map<String, Object> inputSchema = objectMap(tool.get("inputSchema"));
        assertEquals("https://json-schema.org/draft/2020-12/schema", inputSchema.get("$schema"));
        assertEquals(List.of("phrase"), inputSchema.get("required"));
        Map<String, Object> annotations = objectMap(tool.get("annotations"));
        assertEquals(Boolean.TRUE, annotations.get("readOnlyHint"));
        assertEquals(Boolean.FALSE, annotations.get("destructiveHint"));
    }

    @Test
    void callsUrgencyToolWithTextAndStructuredContent() {
        McpProtocolRequest request = request(
                Map.of("Mcp-Method", "tools/call", "Mcp-Name", "getUrgency"),
                Map.of("jsonrpc", "2.0",
                        "id", 99,
                        "method", "tools/call",
                        "params", Map.of(
                                "name", "getUrgency",
                                "arguments", Map.of("phrase", "patient cannot access insulin refill"))));

        McpProtocolResponse response = handler().handle(request);

        assertEquals(Status.OK_200, response.status());
        Map<String, Object> result = result(response);
        assertEquals(8.25, result.get("structuredContent"));
        Map<String, Object> content = firstContent(result);
        assertEquals("text", content.get("type"));
        assertEquals("8.25", content.get("text"));
    }

    @Test
    void rejectsSessionHeader() {
        McpProtocolResponse response = handler().handle(request(Map.of(
                        "MCP-Protocol-Version", "2026-07-28",
                        "Mcp-Method", "ping",
                        "Mcp-Session-Id", "legacy-session"),
                Map.of("jsonrpc", "2.0", "id", 1, "method", "ping")));

        assertError(response, -32600);
    }

    @Test
    void rejectsMismatchedMethodHeader() {
        McpProtocolResponse response = handler().handle(request(Map.of("Mcp-Method", "tools/list"),
                Map.of("jsonrpc", "2.0", "id", 1, "method", "ping")));

        assertError(response, -32600);
    }

    @Test
    void rejectsMissingToolNameHeader() {
        McpProtocolResponse response = handler().handle(request("tools/call"));

        assertError(response, -32602);
    }

    @Test
    void rejectsBlankPhrase() {
        McpProtocolRequest request = request(
                Map.of("Mcp-Method", "tools/call", "Mcp-Name", "getUrgency"),
                Map.of("jsonrpc", "2.0",
                        "id", 1,
                        "method", "tools/call",
                        "params", Map.of(
                                "name", "getUrgency",
                                "arguments", Map.of("phrase", " "))));

        McpProtocolResponse response = handler().handle(request);

        assertError(response, -32602);
    }

    private static StatelessMcpProtocolHandler handler() {
        return new StatelessMcpProtocolHandler(McpUrgencyServer.withScorerSupplier(() -> phrase -> 8.25));
    }

    private static McpProtocolRequest request(String method) {
        return request(Map.of("Mcp-Method", method), Map.of("jsonrpc", "2.0", "id", 1, "method", method));
    }

    private static McpProtocolRequest request(Map<String, String> headers, Map<String, Object> body) {
        var allHeaders = new java.util.LinkedHashMap<String, String>();
        allHeaders.put("MCP-Protocol-Version", "2026-07-28");
        allHeaders.putAll(headers);
        return new McpProtocolRequest(allHeaders, body);
    }

    private static void assertError(McpProtocolResponse response, int code) {
        assertEquals(Status.BAD_REQUEST_400, response.status());
        assertFalse(response.body().containsKey("result"));
        assertEquals(code, objectMap(response.body().get("error")).get("code"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(McpProtocolResponse response) {
        return (Map<String, Object>) response.body().get("result");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstTool(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("tools")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstContent(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("content")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }
}
