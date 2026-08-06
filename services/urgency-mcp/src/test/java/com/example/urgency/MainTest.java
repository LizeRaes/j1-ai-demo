package com.example.urgency;

import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ServerTest
class MainTest {

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
            JsonObject result = result(response.entity().as(JsonObject.class));
            assertEquals("complete", result.stringValue("resultType", ""));
            assertEquals("2026-07-28", result.arrayValue("supportedVersions").orElseThrow()
                    .get(0).orElseThrow().asString().value());
            assertEquals("2026-07-28", result.arrayValue("protocolVersions").orElseThrow()
                    .get(0).orElseThrow().asString().value());
            assertEquals(300_000, result.intValue("ttlMs", 0));
            assertEquals("public", result.stringValue("cacheScope", ""));
        }
    }

    @Test
    void statelessToolsListContainsCacheHints() {
        try (Http1ClientResponse response = statelessPost("tools/list", null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")) {
            assertEquals(Status.OK_200, response.status());
            JsonObject result = result(response.entity().as(JsonObject.class));
            assertEquals(300_000, result.intValue("ttlMs", 0));
            assertEquals("public", result.stringValue("cacheScope", ""));
            assertEquals("getUrgency", firstTool(result).stringValue("name", ""));
        }
    }

    @Test
    void statelessRequestWithLegacySessionHeaderIsRejected() {
        try (Http1ClientResponse response = client.post("/urgency")
                .contentType(MediaTypes.APPLICATION_JSON)
                .header(HeaderValues.create("MCP-Protocol-Version", "2026-07-28"))
                .header(HeaderValues.create("Mcp-Method", "ping"))
                .header(HeaderValues.create("Mcp-Session-Id", "legacy-session"))
                .submit("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")) {
            assertEquals(Status.BAD_REQUEST_400, response.status());
            assertFalse(response.headers().contains(HeaderNames.create("Mcp-Session-Id")));
            assertEquals(-32600, response.entity().as(JsonObject.class)
                    .objectValue("error").orElseThrow().intValue("code", 0));
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

    private static JsonObject result(JsonObject body) {
        return body.objectValue("result").orElseThrow();
    }

    private static JsonObject firstTool(JsonObject result) {
        return result.arrayValue("tools").orElseThrow().get(0).orElseThrow().asObject();
    }
}
