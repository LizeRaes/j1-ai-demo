package com.example.urgency;

import com.example.urgency.mcp.*;
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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

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
    private static final String SCORER_SUPPLIER_LABEL = "urgency scorer supplier";

    private final McpJson json = new McpJson();
    private final McpProtocolHandler handler;

    McpUrgencyServer() {
        this(new LazyUrgencyScorerSupplier());
    }

    static McpUrgencyServer withScorerSupplier(Supplier<UrgencyScorer> scorerSupplier) {
        return new McpUrgencyServer(scorerSupplier);
    }

    private McpUrgencyServer(Supplier<UrgencyScorer> scorerSupplier) {
        handler = new McpProtocolHandler(Objects.requireNonNull(scorerSupplier, SCORER_SUPPLIER_LABEL));
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.post(MCP_PATH, this::handlePost);
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

    private static final class LazyUrgencyScorerSupplier implements Supplier<UrgencyScorer> {
        private final StableValue<UrgencyScorer> inference = StableValue.of();

        @Override
        public UrgencyScorer get() {
            return inference.orElseSet(UrgencyInferenceService::new);
        }
    }
}
