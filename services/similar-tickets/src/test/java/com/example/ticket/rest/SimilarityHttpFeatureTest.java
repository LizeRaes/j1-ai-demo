package com.example.ticket.rest;

import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SimilarityHttpFeatureTest {

    @Test
    void registersRoutesWithConfiguredBasePath() {
        SimilarityEndpoint endpoint = mock(SimilarityEndpoint.class);
        HttpRouting.Builder routing = mock(HttpRouting.Builder.class);
        var feature = new SimilarityHttpFeature(endpoint, "custom/similarity/tickets/");

        feature.setup(routing);

        verify(routing).register(eq("/custom/similarity/tickets"), any(HttpService[].class));
    }

    @Test
    void registersRoutesWithDefaultBasePathWhenConfigIsBlank() {
        SimilarityEndpoint endpoint = mock(SimilarityEndpoint.class);
        HttpRouting.Builder routing = mock(HttpRouting.Builder.class);
        var feature = new SimilarityHttpFeature(endpoint, " ");

        feature.setup(routing);

        verify(routing).register(eq("/api/similarity/tickets"), any(HttpService[].class));
    }
}
