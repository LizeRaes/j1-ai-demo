package com.example.ticket.rest;

import io.helidon.config.Config;
import io.helidon.http.BadRequestException;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import com.example.ticket.dto.SearchRequest;
import com.example.ticket.dto.UpsertRequest;

@Service.Singleton
final class SimilarityHttpFeature implements HttpFeature {

    private static final HttpMediaType MEDIA_TYPE = HttpMediaType.create("application/json");
    private static final String DEFAULT_BASE_PATH = "/api/similarity/tickets";

    private final SimilarityEndpoint endpoint;
    private final String basePath;

    @Service.Inject
    SimilarityHttpFeature(SimilarityEndpoint endpoint, Config config) {
        this.endpoint = endpoint;
        this.basePath = normalizeBasePath(config.get("similarity.tickets.base-path")
                                              .asString()
                                              .orElse(DEFAULT_BASE_PATH));
    }

    SimilarityHttpFeature(SimilarityEndpoint endpoint, String basePath) {
        this.endpoint = endpoint;
        this.basePath = normalizeBasePath(basePath);
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.register(basePath, this::routing);
    }

    private void routing(HttpRules rules) {
        rules.route(HttpRoute.builder()
                .methods(Method.GET)
                .headers(headers -> headers.isAccepted(MEDIA_TYPE))
                .path("/config")
                .handler(this::config)
                .build());
        rules.route(HttpRoute.builder()
                .methods(Method.GET)
                .headers(headers -> headers.isAccepted(MEDIA_TYPE))
                .path("/logs")
                .handler(this::logs)
                .build());
        rules.route(HttpRoute.builder()
                .methods(Method.GET)
                .headers(headers -> headers.isAccepted(MEDIA_TYPE))
                .path("/all")
                .handler(this::all)
                .build());
        rules.route(HttpRoute.builder()
                .methods(Method.DELETE)
                .headers(headers -> headers.isAccepted(MEDIA_TYPE))
                .path("/delete/{ticketId}")
                .handler(this::delete)
                .build());
        rules.route(HttpRoute.builder()
                .methods(Method.POST)
                .headers(headers -> headers.isAccepted(MEDIA_TYPE))
                .path("/search")
                .handler(this::search)
                .build());
        rules.route(HttpRoute.builder()
                .methods(Method.POST)
                .headers(headers -> headers.isAccepted(MEDIA_TYPE))
                .path("/upsert")
                .handler(this::upsert)
                .build());
    }

    private void config(ServerRequest request, ServerResponse response) {
        response.headers().contentType(MEDIA_TYPE);
        response.send(endpoint.config());
    }

    private void logs(ServerRequest request, ServerResponse response) {
        response.headers().contentType(MEDIA_TYPE);
        response.send(endpoint.logs());
    }

    private void all(ServerRequest request, ServerResponse response) {
        response.headers().contentType(MEDIA_TYPE);
        response.send(endpoint.all());
    }

    private void delete(ServerRequest request, ServerResponse response) {
        String ticketId = request.path().pathParameters().first("ticketId")
                .asString()
                .orElseThrow(() -> new BadRequestException("Path parameter ticketId is not present in the request."));
        response.headers().contentType(MEDIA_TYPE);
        response.send(endpoint.delete(ticketId));
    }

    private void search(ServerRequest request, ServerResponse response) {
        response.headers().contentType(MEDIA_TYPE);
        response.send(endpoint.search(request.content().as(SearchRequest.class)));
    }

    private void upsert(ServerRequest request, ServerResponse response) {
        response.headers().contentType(MEDIA_TYPE);
        response.send(endpoint.upsert(request.content().as(UpsertRequest.class)).asStatusResponse());
    }

    private static String normalizeBasePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return DEFAULT_BASE_PATH;
        }
        String path = configuredPath.strip();
        String prefixedPath = path.startsWith("/") ? path : "/" + path;
        return prefixedPath.endsWith("/") && prefixedPath.length() > 1
                ? prefixedPath.substring(0, prefixedPath.length() - 1)
                : prefixedPath;
    }
}
