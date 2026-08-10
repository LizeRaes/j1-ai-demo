package com.example.urgency.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.urgency.McpUrgencyServer;

import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatelessMcpProtocolHandlerTest {

    private static final StatelessMcpProtocolHandler HANDLER =
            new StatelessMcpProtocolHandler(new McpUrgencyServer(() -> _ -> 8.25));

    @Test
    void discoversServerCapabilitiesWithCacheHints() {
        McpProtocolResponse response = HANDLER.handle(request("server/discover"));

        assertEquals(Status.OK_200, response.status());
        JsonObject result = result(response);
        assertEquals("complete", result.stringValue("resultType", ""));
        assertEquals(StatelessMcpProtocolHandler.PROTOCOL_VERSION, result.arrayValue("supportedVersions").orElseThrow()
                .get(0).orElseThrow().asString().value());
        assertEquals(StatelessMcpProtocolHandler.PROTOCOL_VERSION, result.arrayValue("protocolVersions").orElseThrow()
                .get(0).orElseThrow().asString().value());
        assertEquals(300_000, result.intValue("ttlMs", 0));
        assertEquals("public", result.stringValue("cacheScope", ""));
        assertTrue(result.containsKey("capabilities"));
        assertTrue(result.containsKey("serverInfo"));
    }

    @Test
    void listsGetUrgencyToolWithJsonSchema202012() {
        McpProtocolResponse response = HANDLER.handle(request("tools/list"));

        JsonObject result = result(response);
        assertEquals(300_000, result.intValue("ttlMs", 0));
        assertEquals("public", result.stringValue("cacheScope", ""));
        JsonObject tool = firstTool(result);
        assertEquals("getUrgency", tool.stringValue("name", ""));
        JsonObject inputSchema = tool.objectValue("inputSchema").orElseThrow();
        assertEquals("https://json-schema.org/draft/2020-12/schema", inputSchema.stringValue("$schema", ""));
        assertEquals("phrase", inputSchema.arrayValue("required").orElseThrow().get(0).orElseThrow().asString().value());
        JsonObject annotations = tool.objectValue("annotations").orElseThrow();
        assertEquals(Boolean.TRUE, annotations.booleanValue("readOnlyHint").orElseThrow());
        assertEquals(Boolean.FALSE, annotations.booleanValue("destructiveHint").orElseThrow());
    }

    @Test
    void callsUrgencyToolWithTextAndStructuredContent() {
        McpProtocolRequest request = request(
                Map.of("Mcp-Method", "tools/call", "Mcp-Name", "getUrgency"),
                JsonObject.builder()
                        .set("jsonrpc", "2.0")
                        .set("id", 99)
                        .set("method", "tools/call")
                        .set("params", params -> params
                                .set("name", "getUrgency")
                                .set("arguments", arguments -> arguments
                                        .set("phrase", "patient cannot access insulin refill")))
                        .build());

        McpProtocolResponse response = HANDLER.handle(request);

        assertEquals(Status.OK_200, response.status());
        JsonObject result = result(response);
        assertEquals(8.25, result.doubleValue("structuredContent", 0.0));
        JsonObject content = result.arrayValue("content").orElseThrow().get(0).orElseThrow().asObject();
        assertEquals("text", content.stringValue("type", ""));
        assertEquals("8.25", content.stringValue("text", ""));
    }

    @Test
    void rejectsSessionHeaderRemovedByStatelessProtocol() {
        McpProtocolResponse response = HANDLER.handle(request(Map.of(
                        "MCP-Protocol-Version", "2026-07-28",
                        "Mcp-Method", "ping",
                        "Mcp-Session-Id", "legacy-session"),
                JsonObject.builder()
                        .set("jsonrpc", "2.0")
                        .set("id", 1)
                        .set("method", "ping")
                        .build()));

        assertError(response, -32600);
    }

    @Test
    void rejectsMismatchedMethodHeader() {
        McpProtocolResponse response = HANDLER.handle(request(Map.of("Mcp-Method", "tools/list"),
                JsonObject.builder()
                        .set("jsonrpc", "2.0")
                        .set("id", 1)
                        .set("method", "ping")
                        .build()));

        assertError(response, -32600);
    }

    @Test
    void rejectsMissingToolNameHeader() {
        McpProtocolResponse response = HANDLER.handle(request("tools/call"));

        assertError(response, -32602);
    }

    @Test
    void rejectsBlankPhrase() {
        McpProtocolRequest request = request(
                Map.of("Mcp-Method", "tools/call", "Mcp-Name", "getUrgency"),
                JsonObject.builder()
                        .set("jsonrpc", "2.0")
                        .set("id", 1)
                        .set("method", "tools/call")
                        .set("params", params -> params
                                .set("name", "getUrgency")
                                .set("arguments", arguments -> arguments.set("phrase", " ")))
                        .build());

        McpProtocolResponse response = HANDLER.handle(request);

        assertError(response, -32602);
    }

    private static McpProtocolRequest request(String method) {
        return request(Map.of("Mcp-Method", method), JsonObject.builder()
                .set("jsonrpc", "2.0")
                .set("id", 1)
                .set("method", method)
                .build());
    }

    private static McpProtocolRequest request(Map<String, String> headers, JsonObject body) {
        var allHeaders = new LinkedHashMap<String, String>();
        allHeaders.put("MCP-Protocol-Version", "2026-07-28");
        allHeaders.putAll(headers);
        return new McpProtocolRequest(allHeaders, body);
    }

    private static void assertError(McpProtocolResponse response, int code) {
        assertEquals(Status.BAD_REQUEST_400, response.status());
        assertFalse(response.body().containsKey("result"));
        assertEquals(code, response.body().objectValue("error").orElseThrow().intValue("code", 0));
    }

    private static JsonObject result(McpProtocolResponse response) {
        return response.body().objectValue("result").orElseThrow();
    }

    private static JsonObject firstTool(JsonObject result) {
        return result.arrayValue("tools").orElseThrow().get(0).orElseThrow().asObject();
    }
}
