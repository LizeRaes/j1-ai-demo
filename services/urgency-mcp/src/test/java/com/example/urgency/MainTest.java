package com.example.urgency;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import org.junit.jupiter.api.Test;

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

    private void initializeMcpSession() {
        try (Http1ClientResponse response = client.post("/urgency")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit(INITIALIZE_REQUEST)) {
            assertTrue(response.status() == Status.OK_200);
        }
    }

    private void assertHealthyEndpoint(String path) {
        try (Http1ClientResponse response = client.get(path).request()) {
            Status status = response.status();

            assertTrue(status == Status.OK_200 || status == Status.NO_CONTENT_204);
        }
    }
}
