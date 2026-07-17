package com.example.urgency;

import java.util.logging.Logger;

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

    private static final String URGENCY_PATH_SEGMENT = "urgency";
    private static final String REQUEST_LOG_PREFIX = "MCP request received: ";
    private static final String REQUEST_LOG_SEPARATOR = " ";
    private static final String REQUEST_LOG_SUFFIX = " (stateless urgency adapter)";
    private static final Logger log = Logger.getLogger(McpRequestLoggingFeature.class.getName());

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter(this::logMcpRequests);
    }

    private void logMcpRequests(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        String path = req.prologue().uriPath().path();
        if (path != null && path.contains(URGENCY_PATH_SEGMENT)) {
            String method = req.prologue().method().text();
            log.info(REQUEST_LOG_PREFIX + method + REQUEST_LOG_SEPARATOR + path + REQUEST_LOG_SUFFIX);
        }
        chain.proceed();
    }
}
