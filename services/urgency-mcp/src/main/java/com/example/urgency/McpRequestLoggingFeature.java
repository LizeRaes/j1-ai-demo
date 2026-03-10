package com.example.urgency;

import java.util.logging.Logger;

import io.helidon.service.registry.Service;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

/**
 * Logs every MCP request (tools/list, tools/call, initialize, etc.) so we can see
 * if the model/client is even trying to reach the MCP server.
 */
@Service.Singleton
final class McpRequestLoggingFeature implements HttpFeature {

    private static final Logger log = Logger.getLogger(McpRequestLoggingFeature.class.getName());

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter(this::logMcpRequests);
    }

    private void logMcpRequests(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        String path = req.prologue().uriPath().path();
        if (path != null && path.contains("urgency")) {
            String method = req.prologue().method().text();
            log.info("MCP request received: " + method + " " + path + " (Helidon urgency server)");
        }
        chain.proceed();
    }
}
