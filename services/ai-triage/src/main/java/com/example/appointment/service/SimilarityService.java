package com.example.appointment.service;

import com.example.appointment.dto.SimilaritySearchRequest;
import com.example.appointment.dto.SimilaritySearchResponse;
import com.example.appointment.dto.TicketSyncUpsertRequest;
import com.example.appointment.external.SimilarityClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class SimilarityService {

    @Inject
    @RestClient
    SimilarityClient similarityClient;

    @ConfigProperty(name = "ai-triage.similarity.max-results")
    Integer maxResults;

    @ConfigProperty(name = "ai-triage.similarity.min-score")
    Double minScore;

    public SimilaritySearchResponse searchSimilarTickets(String ticketType, String text, Long ticketId) {
        SimilaritySearchRequest request = new SimilaritySearchRequest(ticketType, text, maxResults, minScore, ticketId);
        return similarityClient.search(request);
    }

    public void upsertTicket(Long ticketId, String ticketType, String text) {
        TicketSyncUpsertRequest request = new TicketSyncUpsertRequest(ticketId, ticketType, text);
        try (Response response = similarityClient.upsert(request)) {
            if (response.getStatus() >= 400) {
                throw new IllegalStateException("Similarity upsert failed with status " + response.getStatus());
            }
        }
    }

    public void deleteTicket(Long ticketId) {
        try (Response response = similarityClient.delete(ticketId)) {
            if (response.getStatus() >= 400) {
                throw new IllegalStateException("Similarity delete failed with status " + response.getStatus());
            }
        }
    }
}
