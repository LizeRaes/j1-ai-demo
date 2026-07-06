package com.example.ticket.rest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.config.Config;
import io.helidon.config.MapConfigSource;

import com.example.ticket.dto.SearchRequest;
import com.example.ticket.model.SimilarTicket;
import com.example.ticket.model.TicketSearchQuery;
import com.example.ticket.service.DemoDataService;
import com.example.ticket.service.EmbeddingService;
import com.example.ticket.service.LogService;
import com.example.ticket.service.TicketStore;
import com.example.ticket.service.VectorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimilarityEndpointTest {

    @Test
    void configUsesConfiguredDefaultZoom() {
        SimilarityEndpoint endpoint = endpoint(config(Map.of("ui.font.zoom.default", "125")));

        assertEquals(125, endpoint.config().get("defaultZoom"));
    }

    @Test
    void searchUsesConfiguredDefaultsWhenRequestOmitsScoring() {
        VectorService vectorService = mock(VectorService.class);
        when(vectorService.searchSimilar(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new SimilarTicket(77L, 0.9)));
        SimilarityEndpoint endpoint = endpoint(
                config(Map.of(
                        "similarity.tickets.search.max-results", "9",
                        "similarity.tickets.search.min-score", "0.35")),
                vectorService,
                mock(DemoDataService.class));

        var response = endpoint.search(new SearchRequest("BUG_APP", "button disabled", null, null, 912L));

        ArgumentCaptor<TicketSearchQuery> queryCaptor = ArgumentCaptor.forClass(TicketSearchQuery.class);
        verify(vectorService).searchSimilar(queryCaptor.capture());
        TicketSearchQuery query = queryCaptor.getValue();
        assertEquals(9, query.maxResults());
        assertEquals(0.35, query.minScore());
        assertEquals(912L, query.excludeTicketId());
        assertEquals(List.of(77L), response.relatedTicketIds());
    }

    @Test
    void beforeStartLoadsDemoDataWhenEnabled() {
        DemoDataService demoDataService = mock(DemoDataService.class);
        when(demoDataService.loadDemoDataAsync()).thenReturn(CompletableFuture.completedStage(null));
        SimilarityEndpoint endpoint = endpoint(
                config(Map.of("DemoData", "true")),
                mock(VectorService.class),
                demoDataService);

        endpoint.beforeStart();

        verify(demoDataService).loadDemoDataAsync();
    }

    private static SimilarityEndpoint endpoint(Config config) {
        return endpoint(config, mock(VectorService.class), mock(DemoDataService.class));
    }

    private static SimilarityEndpoint endpoint(Config config, VectorService vectorService, DemoDataService demoDataService) {
        return new SimilarityEndpoint(
                config,
                demoDataService,
                mock(EmbeddingService.class),
                mock(TicketStore.class),
                vectorService,
                mock(LogService.class));
    }

    private static Config config(Map<String, String> values) {
        return Config.create(() -> MapConfigSource.create(values));
    }
}
