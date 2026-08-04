package com.example.urgency;

import java.util.List;
import java.util.Map;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ServerTest
class MainTest {

    private static final GenericType<Map<String, Object>> RESPONSE_BODY_TYPE = GenericType.<Map<String, Object>>builder()
            .baseType(Map.class)
            .addGenericParameter(String.class)
            .addGenericParameter(Object.class)
            .build();
    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-06-18","capabilities":{},
             "clientInfo":{"name":"urgency-mcp-test","version":"1.0.0"}}}
            """;
    private final Http1Client client;

    MainTest(Http1Client client) {
        this.client = client;
    }

    @Test
    void exposesHealthEndpoint() {
        assertHealthyEndpoint("/observe/health");
    }

    @Test
    void exposesReadinessEndpoint() {
        assertHealthyEndpoint("/observe/health/ready");
    }

    @Test
    void mcpInitializeAcceptsSupported2025Protocol() {
        initializeMcpSession();
    }

    @Test
    void statelessDiscoverDoesNotCreateSession() {
        try (Http1ClientResponse response = statelessPost("server/discover", null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\"}")) {
            assertEquals(Status.OK_200, response.status());
            assertFalse(response.headers().contains(HeaderNames.create("Mcp-Session-Id")));
            Map<String, Object> result = result(response.entity().as(RESPONSE_BODY_TYPE));
            assertEquals(List.of("2026-07-28"), result.get("protocolVersions"));
            assertNumberEquals(300_000, result.get("ttlMs"));
            assertEquals("public", result.get("cacheScope"));
        }
    }

    @Test
    void statelessToolsListContainsCacheHints() {
        try (Http1ClientResponse response = statelessPost("tools/list", null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")) {
            assertEquals(Status.OK_200, response.status());
            Map<String, Object> result = result(response.entity().as(RESPONSE_BODY_TYPE));
            assertNumberEquals(300_000, result.get("ttlMs"));
            assertEquals("public", result.get("cacheScope"));
            assertEquals("getUrgency", firstTool(result).get("name"));
        }
    }

    @Test
    void statelessRejectsLegacySessionHeader() {
        try (Http1ClientResponse response = client.post("/urgency")
                .contentType(MediaTypes.APPLICATION_JSON)
                .header(HeaderValues.create("MCP-Protocol-Version", "2026-07-28"))
                .header(HeaderValues.create("Mcp-Method", "ping"))
                .header(HeaderValues.create("Mcp-Session-Id", "legacy-session"))
                .submit("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")) {
            assertEquals(Status.BAD_REQUEST_400, response.status());
            Map<String, Object> error = objectMap(response.entity().as(RESPONSE_BODY_TYPE).get("error"));
            assertNumberEquals(-32600, error.get("code"));
        }
    }

    private void initializeMcpSession() {
        try (Http1ClientResponse response = client.post("/urgency")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit(INITIALIZE_REQUEST)) {
            assertTrue(response.status() == Status.OK_200);
        }
    }

    private Http1ClientResponse statelessPost(String method, String name, String body) {
        var request = client.post("/urgency")
                .contentType(MediaTypes.APPLICATION_JSON)
                .header(HeaderValues.create("MCP-Protocol-Version", "2026-07-28"))
                .header(HeaderValues.create("Mcp-Method", method));
        if (name != null) {
            request.header(HeaderValues.create("Mcp-Name", name));
        }
        return request.submit(body);
    }

    private void assertHealthyEndpoint(String path) {
        try (Http1ClientResponse response = client.get(path).request()) {
            Status status = response.status();

            assertTrue(status == Status.OK_200 || status == Status.NO_CONTENT_204);
        }
    }

    private static void assertNumberEquals(int expected, Object actual) {
        assertEquals(expected, ((Number) actual).intValue());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> body) {
        return (Map<String, Object>) body.get("result");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstTool(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("tools")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }
}
