package com.example.urgency;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.example.urgency.mcp.McpProtocolRequest;
import com.example.urgency.mcp.McpProtocolResponse;
import com.example.urgency.mcp.StatelessMcpProtocolHandler;

import io.helidon.common.GenericType;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

/**
 * Logs urgency MCP HTTP requests so operators can see client reachability.
 */
@Service.Singleton
final class McpRequestLoggingFeature implements HttpFeature {

    private static final GenericType<Map<String, Object>> REQUEST_BODY_TYPE = GenericType.<Map<String, Object>>builder()
            .baseType(Map.class)
            .addGenericParameter(String.class)
            .addGenericParameter(Object.class)
            .build();
    private static final HttpMediaType MEDIA_TYPE = HttpMediaType.create("application/json");
    private static final String URGENCY_PATH = McpUrgencyServer.MCP_PATH;
    private static final String URGENCY_PATH_SEGMENT = "urgency";
    private static final HeaderName PROTOCOL_VERSION_HEADER = HeaderNames.create("MCP-Protocol-Version");
    private static final String REQUEST_LOG_PREFIX = "MCP request received: ";
    private static final String REQUEST_LOG_SEPARATOR = " ";
    private static final String REQUEST_LOG_SUFFIX = " (Helidon MCP server)";
    private static final Logger log = Logger.getLogger(McpRequestLoggingFeature.class.getName());

    private final StatelessMcpProtocolHandler statelessHandler;

    @Service.Inject
    McpRequestLoggingFeature(StatelessMcpProtocolHandler statelessHandler) {
        this.statelessHandler = statelessHandler;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter(this::handleMcpRequests);
    }

    private void handleMcpRequests(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        String path = req.prologue().uriPath().path();
        if (path != null && path.contains(URGENCY_PATH_SEGMENT)) {
            String method = req.prologue().method().text();
            log.info(REQUEST_LOG_PREFIX + method + REQUEST_LOG_SEPARATOR + path + REQUEST_LOG_SUFFIX);
        }
        if (isStatelessMcpRequest(req)) {
            handleStatelessRequest(req, res);
            return;
        }
        chain.proceed();
    }

    private void handleStatelessRequest(RoutingRequest req, RoutingResponse res) {
        if (req.prologue().method() != Method.POST) {
            send(res, methodNotAllowed());
            return;
        }
        try {
            Map<String, Object> body = req.content().as(REQUEST_BODY_TYPE);
            McpProtocolRequest request = new McpProtocolRequest(headers(req), body);
            send(res, statelessHandler.handle(request));
        } catch (RuntimeException e) {
            send(res, StatelessMcpProtocolHandler.parseError());
        }
    }

    private static McpProtocolResponse methodNotAllowed() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", null);
        body.put("error", Map.of("code", -32600, "message", "MCP 2026-07-28 requests must use POST"));
        return new McpProtocolResponse(Status.METHOD_NOT_ALLOWED_405, body);
    }

    private static boolean isStatelessMcpRequest(RoutingRequest req) {
        String path = req.prologue().uriPath().path();
        return URGENCY_PATH.equals(path) && req.headers().value(PROTOCOL_VERSION_HEADER).isPresent();
    }

    private static Map<String, String> headers(RoutingRequest req) {
        return req.headers()
                .toMap()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().getFirst()));
    }

    private static void send(RoutingResponse response, McpProtocolResponse protocolResponse) {
        response.status(protocolResponse.status());
        response.headers().contentType(MEDIA_TYPE);
        response.send(protocolResponse.body());
        response.commit();
    }
}
