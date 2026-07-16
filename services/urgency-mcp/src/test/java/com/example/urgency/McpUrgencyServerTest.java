package com.example.urgency;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.example.urgency.service.UrgencyScorer;

import io.helidon.webserver.WebServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpUrgencyServerTest {

    private static WebServer server;
    private static HttpClient client;
    private static URI endpoint;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void startServer() {
        server = WebServer.builder()
                .port(0)
                .featuresDiscoverServices(false)
                .routing(routing -> new McpUrgencyServer().setup(routing))
                .build()
                .start();
        client = HttpClient.newHttpClient();
        endpoint = URI.create("http://localhost:" + server.port() + McpUrgencyServer.MCP_PATH);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @AfterEach
    void resetScorer() {
        McpUrgencyServer.clearInferenceForTesting();
    }

    @Test
    void rejectsMissingProtocolVersion() throws Exception {
        McpResponse response = post(McpRequest.withoutProtocol(requestBody("tools/list")));

        assertJsonRpcError(response, "Missing MCP-Protocol-Version header");
    }

    @Test
    void rejectsUnsupportedProtocolVersion() throws Exception {
        McpResponse response = post(McpRequest.forMethod("tools/list")
                .protocolVersion("2025-06-18")
                .body(requestBody("tools/list")));

        assertJsonRpcError(response, "Unsupported MCP protocol version");
    }

    @Test
    void rejectsMissingMethodHeader() throws Exception {
        McpResponse response = post(McpRequest.withoutMethod(requestBody("tools/list")));

        assertJsonRpcError(response, "Missing Mcp-Method header");
    }

    @Test
    void rejectsMismatchedMethodHeader() throws Exception {
        McpResponse response = post(McpRequest.forMethod("ping")
                .body(requestBody("tools/list")));

        assertJsonRpcError(response, "Mcp-Method does not match JSON-RPC method");
    }

    @Test
    void rejectsMissingToolNameHeader() throws Exception {
        McpResponse response = post(McpRequest.forMethod("tools/call")
                .body(toolsCallBody("getUrgency", "patient cannot access insulin refill")));

        assertJsonRpcError(response, "Missing Mcp-Name header");
    }

    @Test
    void rejectsMismatchedToolNameHeader() throws Exception {
        McpResponse response = post(McpRequest.forMethod("tools/call")
                .name("otherTool")
                .body(toolsCallBody("getUrgency", "patient cannot access insulin refill")));

        assertJsonRpcError(response, "Mcp-Name does not match requested tool name");
    }

    @Test
    void doesNotRequireSessionHeader() throws Exception {
        McpResponse response = post(McpRequest.forMethod("ping").body(requestBody("ping")));

        assertEquals(200, response.status());
        assertTrue(response.body().get("result").isEmpty());
        assertTrue(response.sessionHeaders().isEmpty());
    }

    @Test
    void serverDiscoverWorks() throws Exception {
        McpResponse response = post(McpRequest.forMethod("server/discover")
                .body(requestBody("server/discover")));

        JsonNode result = response.body().get("result");
        assertEquals(McpUrgencyServer.MCP_PROTOCOL_VERSION, result.get("protocolVersion").asText());
        assertEquals(McpUrgencyServer.MCP_SERVER_NAME, result.get("serverInfo").get("name").asText());
        assertNotNull(result.get("capabilities").get("tools"));
    }

    @Test
    void toolsListIncludesSchemaAndCacheMetadata() throws Exception {
        McpResponse response = post(McpRequest.forMethod("tools/list").body(requestBody("tools/list")));

        JsonNode tool = response.body()
                .get("result")
                .get("tools")
                .get(0);
        JsonNode schema = tool.get("inputSchema");
        assertEquals(McpUrgencyServer.TOOL_NAME, tool.get("name").asText());
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.get("$schema").asText());
        assertEquals("string", schema.get("properties").get("phrase").get("type").asText());
        assertEquals(McpUrgencyServer.TOOL_CACHE_TTL_MS, tool.get("ttlMs").longValue());
        assertEquals(McpUrgencyServer.TOOL_CACHE_SCOPE, tool.get("cacheScope").asText());
    }

    @Test
    void toolsCallReturnsUrgencyScore() throws Exception {
        StubScorer scorer = new StubScorer(7.5);
        McpUrgencyServer.replaceInferenceForTesting(scorer);

        McpResponse response = post(McpRequest.forMethod("tools/call")
                .name("getUrgency")
                .body(toolsCallBody("getUrgency", "patient cannot access billing portal")));

        JsonNode result = response.body().get("result");
        JsonNode content = result.get("content").get(0);
        assertEquals("patient cannot access billing portal", scorer.complaint);
        assertEquals("text", content.get("type").asText());
        assertEquals("7.5", content.get("text").asText());
        assertEquals(7.5, result.get("structuredContent").get("score").doubleValue());
        assertFalse(result.get("isError").asBoolean());
    }

    @Test
    void requiresReplacementScorer() {
        assertThrows(NullPointerException.class, () -> McpUrgencyServer.replaceInferenceForTesting(null));
    }

    private static void assertJsonRpcError(McpResponse response, String message) {
        assertEquals(200, response.status());
        assertEquals(message, response.body().get("error").get("message").asText());
    }

    private static McpResponse post(McpRequest request) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(request.body()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        request.protocolVersion().ifPresent(value -> builder.header("MCP-Protocol-Version", value));
        request.method().ifPresent(value -> builder.header("Mcp-Method", value));
        request.name().ifPresent(value -> builder.header("Mcp-Name", value));

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new McpResponse(response.statusCode(), jsonObject(response.body()), response.headers().allValues("Mcp-Session-Id"));
    }

    private static JsonNode jsonObject(String text) throws Exception {
        return JSON.readTree(text);
    }

    private static String requestBody(String method) {
        ObjectNode body = JSON.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", method);
        return body.toString();
    }

    private static String toolsCallBody(String name, String phrase) {
        ObjectNode arguments = JSON.createObjectNode().put("phrase", phrase);
        ObjectNode params = JSON.createObjectNode()
                .put("name", name)
                .set("arguments", arguments);
        ObjectNode body = JSON.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "tools/call")
                .set("params", params);
        return body.toString();
    }

    private record McpRequest(String body, java.util.Optional<String> protocolVersion,
                              java.util.Optional<String> method, java.util.Optional<String> name) {
        private static McpRequest forMethod(String method) {
            return new McpRequest("", java.util.Optional.of(McpUrgencyServer.MCP_PROTOCOL_VERSION),
                    java.util.Optional.of(method), java.util.Optional.empty());
        }

        private static McpRequest withoutProtocol(String body) {
            return new McpRequest(body, java.util.Optional.empty(), java.util.Optional.of("tools/list"),
                    java.util.Optional.empty());
        }

        private static McpRequest withoutMethod(String body) {
            return new McpRequest(body, java.util.Optional.of(McpUrgencyServer.MCP_PROTOCOL_VERSION),
                    java.util.Optional.empty(), java.util.Optional.empty());
        }

        private McpRequest protocolVersion(String value) {
            return new McpRequest(body, java.util.Optional.of(value), method, name);
        }

        private McpRequest body(String value) {
            return new McpRequest(value, protocolVersion, method, name);
        }

        private McpRequest name(String value) {
            return new McpRequest(body, protocolVersion, method, java.util.Optional.of(value));
        }
    }

    private record McpResponse(int status, JsonNode body, List<String> sessionHeaders) {
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
