package com.example.ticket.rest;

import java.util.List;

import io.helidon.http.BadRequestException;

import com.example.ticket.dto.SearchRequest;
import com.example.ticket.dto.UpsertRequest;
import com.example.ticket.model.SimilarTicket;
import com.example.ticket.model.TicketData;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityMapperTest {

    private static final SimilaritySearchDefaults SEARCH_DEFAULTS = new SimilaritySearchDefaults(7, 0.25);

    @Test
    void mapsSearchRequestToInternalQueryWithDefaults() {
        var query = SimilarityMapper.toSearchQuery(new SearchRequest("BUG_APP", "button disabled", null, null, 912L), SEARCH_DEFAULTS)
                .asTicketSearchQuery();

        assertEquals("button disabled", query.text());
        assertEquals(7, query.maxResults());
        assertEquals(0.25, query.minScore());
        assertEquals(912L, query.excludeTicketId());
    }

    @Test
    void rejectsInvalidSearchRequest() {
        assertThrows(BadRequestException.class,
                     () -> SimilarityMapper.toSearchQuery(new SearchRequest("BUG_APP", null, 5, 0.4, 912L), SEARCH_DEFAULTS));
        assertThrows(BadRequestException.class,
                     () -> SimilarityMapper.toSearchQuery(new SearchRequest("BUG_APP", "text", 0, 0.4, 912L), SEARCH_DEFAULTS));
    }

    @Test
    void mapsUpsertRequestToInternalTicketData() {
        float[] embedding = {1.0f, 2.0f};

        var ticket = SimilarityMapper.toTicketData(new UpsertRequest(912L, "BUG_APP", "button disabled"), embedding)
                .asTicketData();

        assertEquals(912L, ticket.ticketId());
        assertEquals("BUG_APP", ticket.ticketType());
        assertEquals("button disabled", ticket.text());
        assertArrayEquals(embedding, ticket.vector());
        assertTrue(ticket.timestamp() > 0);
    }

    @Test
    void mapsInternalResultsToSearchResponsePayload() {
        var response = SimilarityMapper.toSearchResponse(List.of(
                new SimilarTicket(12L, 0.91),
                new SimilarTicket(15L, 0.82)))
                .asSearchResponse();

        assertEquals(List.of(12L, 15L), response.relatedTicketIds());
    }

    @Test
    void mapsInternalTicketsToSortedResponsePayload() {
        var response = SimilarityMapper.toTicketsResponse(List.of(
                new TicketData(1L, "BILLING", "billing text", new float[]{1.0f}, 10L),
                new TicketData(3L, "BUG_APP", "bug text", new float[]{3.0f}, 20L)))
                .asTicketsResponse();

        assertEquals(3L, response.tickets().getFirst().id());
        assertEquals("BUG_APP", response.tickets().getFirst().type());
        assertEquals("bug text", response.tickets().getFirst().text());
    }

    @Test
    void serializesAndDeserializesSearchPayload() throws Exception {
        try (var jsonb = JsonbBuilder.create()) {
            String json = jsonb.toJson(new SearchRequest("BUG_APP", "button disabled", 3, 0.7, 912L));

            assertTrue(json.contains("ticketType"));
            assertTrue(json.contains("maxResults"));

            SearchRequest request = jsonb.fromJson(json, SearchRequest.class);
            assertEquals("BUG_APP", request.ticketType());
            assertEquals("button disabled", request.text());
            assertEquals(3, request.maxResults());
            assertEquals(0.7, request.minScore());
            assertEquals(912L, request.ticketId());
        }
    }
}
