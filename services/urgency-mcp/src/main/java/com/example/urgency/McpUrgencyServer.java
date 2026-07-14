package com.example.urgency;

import java.util.Objects;
import java.util.Optional;

import com.example.urgency.mcp.McpDiscoveryDocument;
import com.example.urgency.mcp.McpJson;
import com.example.urgency.mcp.McpProtocolHandler;
import com.example.urgency.mcp.McpRequestHeaders;
import com.example.urgency.mcp.McpRequestValidator;
import com.example.urgency.mcp.McpTool;
import com.example.urgency.mcp.McpToolCatalog;
import com.example.urgency.service.UrgencyInferenceService;
import com.example.urgency.service.UrgencyScorer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@Service.Singleton
final class McpUrgencyServer implements HttpFeature {

    static final String MCP_PATH = "/urgency";
    static final String MCP_SERVER_NAME = McpDiscoveryDocument.SERVER_NAME;
    static final String MCP_PROTOCOL_VERSION = McpRequestValidator.PROTOCOL_VERSION;
    static final String TOOL_NAME = McpTool.GET_URGENCY.toolName();
    static final long TOOL_CACHE_TTL_MS = McpToolCatalog.CACHE_TTL_MS;
    static final String TOOL_CACHE_SCOPE = McpToolCatalog.CACHE_SCOPE;

    private static final HeaderName MCP_PROTOCOL_VERSION_HEADER = HeaderNames.create("MCP-Protocol-Version");
    private static final HeaderName MCP_METHOD_HEADER = HeaderNames.create("Mcp-Method");
    private static final HeaderName MCP_NAME_HEADER = HeaderNames.create("Mcp-Name");
    private static final String REPLACEMENT_LABEL = "replacement";

    private static volatile UrgencyScorer inference;

    private final McpJson json = new McpJson();
    private final McpProtocolHandler handler = new McpProtocolHandler(McpUrgencyServer::inference);

    static UrgencyScorer replaceInferenceForTesting(UrgencyScorer replacement) {
        UrgencyScorer previous = inference;
        inference = Objects.requireNonNull(replacement, REPLACEMENT_LABEL);
        return previous;
    }

    static void clearInferenceForTesting() {
        inference = null;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.post(MCP_PATH, this::handlePost);
    }

    private static UrgencyScorer inference() {
        UrgencyScorer cached = inference;
        if (cached != null) {
            return cached;
        }
        synchronized (McpUrgencyServer.class) {
            if (inference == null) {
                inference = new UrgencyInferenceService();
            }
            return inference;
        }
    }

    private void handlePost(ServerRequest request, ServerResponse response) {
        ObjectNode body = json.readObject(request.content().inputStream());
        ObjectNode result = handler.handle(headers(request), body);
        response.status(Status.OK_200)
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .send(json.write(result));
    }

    private static McpRequestHeaders headers(ServerRequest request) {
        return new McpRequestHeaders(
                header(request, MCP_PROTOCOL_VERSION_HEADER),
                header(request, MCP_METHOD_HEADER),
                header(request, MCP_NAME_HEADER));
    }

    private static Optional<String> header(ServerRequest request, HeaderName name) {
        return request.headers().first(name);
    }
}
