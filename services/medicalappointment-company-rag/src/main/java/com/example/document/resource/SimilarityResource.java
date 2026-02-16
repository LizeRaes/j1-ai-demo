package com.example.document.resource;

import com.example.document.dto.DeleteRequest;
import com.example.document.dto.LogsResponse;
import com.example.document.dto.SearchRequest;
import com.example.document.dto.SearchResponse;
import com.example.document.dto.StatusResponse;
import com.example.document.dto.TicketsResponse;
import com.example.document.dto.UpsertRequest;
import com.example.document.service.EmbeddingService;
import com.example.document.service.LogService;
import com.example.document.service.QdrantService;
import com.example.document.service.TicketStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/similarity/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SimilarityResource {

    @Inject
    EmbeddingService embeddingService;

    @Inject
    QdrantService qdrantService;

    @Inject
    TicketStore ticketStore;

    @Inject
    LogService logService;

    @ConfigProperty(name = "ui.font.zoom.default", defaultValue = "100")
    int defaultZoom;

    @POST
    @Path("/upsert")
    public StatusResponse upsert(UpsertRequest request) {
        if (request.getTicketId() == null || request.getText() == null || request.getTicketType() == null) {
            throw new IllegalArgumentException("ticketId, ticketType, and text are required");
        }

        logService.addLog("Received ticket #" + request.getTicketId() + " via upsert endpoint", "upsert");

        // Generate embedding for the text
        float[] embedding = embeddingService.embed(request.getText());

        // Upsert into Qdrant
        qdrantService.upsertPoint(request.getTicketId(), request.getTicketType(), request.getText(), embedding);

        // Store in memory for frontend display
        ticketStore.storeTicket(request.getTicketId(), request.getTicketType(), request.getText(), embedding);

        logService.addLog("Stored embedding for ticket #" + request.getTicketId(), "upsert");

        return new StatusResponse("OK");
    }

    @POST
    @Path("/delete")
    public StatusResponse delete(DeleteRequest request) {
        if (request.getTicketId() == null) {
            throw new IllegalArgumentException("ticketId is required");
        }

        logService.addLog("Delete request for ticket #" + request.getTicketId(), "delete");

        // Delete from Qdrant (idempotent - no-op if doesn't exist)
        qdrantService.deletePoint(request.getTicketId());

        // Remove from memory store
        ticketStore.removeTicket(request.getTicketId());

        return new StatusResponse("OK");
    }

    @POST
    @Path("/search")
    public SearchResponse search(SearchRequest request) {
        if (request.getText() == null || request.getTicketId() == null) {
            throw new IllegalArgumentException("text and ticketId are required");
        }

        int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 5;
        double minScore = request.getMinScore() != null ? request.getMinScore() : 0.0; // Default: no threshold

        logService.addLog("Similarity search for ticket #" + request.getTicketId() + ", text: \"" +
                (request.getText().length() > 50 ? request.getText().substring(0, 50) + "..." : request.getText()) +
                "\", minScore: " + minScore, "search");

        // Search in Qdrant using EmbeddingStore.search - no ticket type filter, just exclude the ticket itself
        var searchResults = qdrantService.searchSimilar(
                request.getText(),
                maxResults,
                minScore,
                request.getTicketId() // Exclude this ticket from results
        );

        // Extract ticket IDs for response
        var relatedTicketIds = searchResults.stream()
                .map(result -> result.ticketId)
                .collect(java.util.stream.Collectors.toList());

        // Log with scores
        String logMessage = "Returned " + searchResults.size() + " similar tickets: " +
                searchResults.stream()
                        .map(result -> result.ticketId + " (" + String.format("%.3f", result.score) + ")")
                        .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        logService.addLog(logMessage, "search");

        return new SearchResponse(relatedTicketIds);
    }

    @GET
    @Path("/all")
    public TicketsResponse getAllTickets() {
        // Get from Qdrant (persisted data, survives restarts)
        var qdrantPoints = qdrantService.getAllPoints();

        // Also get from in-memory store (for recently added tickets that might not be in Qdrant yet)
        var inMemoryTickets = ticketStore.getAllTickets();

        // Create a map of ticketId -> TicketInfo, prioritizing Qdrant data (persisted)
        Map<Long, TicketsResponse.TicketInfo> ticketMap = new HashMap<>();

        // Add Qdrant points first (these are persisted)
        for (var point : qdrantPoints) {
            ticketMap.put(point.ticketId, new TicketsResponse.TicketInfo(
                    point.ticketId,
                    point.ticketType,
                    point.text,
                    point.vector
            ));
        }

        // Override with in-memory data where available (might have more recent updates)
        for (var ticket : inMemoryTickets) {
            ticketMap.put(ticket.ticketId, new TicketsResponse.TicketInfo(
                    ticket.ticketId,
                    ticket.ticketType,
                    ticket.text,
                    ticket.vector
            ));
        }

        // Sort by ticketId descending (latest first, assuming higher IDs are newer)
        var tickets = ticketMap.values().stream()
                .sorted((a, b) -> Long.compare(b.getTicketId(), a.getTicketId()))
                .collect(Collectors.toList());

        return new TicketsResponse(tickets);
    }

    @GET
    @Path("/logs")
    public LogsResponse getLogs() {
        var logs = logService.getLogs().stream()
                .map(l -> new LogsResponse.LogInfo(l.message, l.type, l.timestamp))
                .collect(Collectors.toList());

        return new LogsResponse(logs);
    }

    @GET
    @Path("/config")
    public Map<String, Object> getConfig() {
        return Map.of("defaultZoom", defaultZoom);
    }
}
