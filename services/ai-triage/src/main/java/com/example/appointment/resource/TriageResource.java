package com.example.appointment.resource;

import com.example.appointment.dto.*;
import com.example.appointment.service.AiTriageAssistant;
import com.example.appointment.service.DocumentService;
import com.example.appointment.service.EventLogService;
import com.example.appointment.service.SimilarityService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.List;

@Path("/api/triage/v1")
public class TriageResource {

    private static final Logger LOG = Logger.getLogger(TriageResource.class);

    @Inject
    AiTriageAssistant aiTriageAssistant;

    @Inject
    SimilarityService similarityService;

    @Inject
    DocumentService documentService;

    @Inject
    EventLogService eventLogService;

    @POST
    @Path("/classify")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    public TriageResponse classify(TriageRequest request) {
        Long ticketId = request.ticketId();

        try {
            eventLogService.addEvent("INFO", "Received triage request for ticket " + ticketId, ticketId);

            eventLogService.addEvent("INFO", "Calling AI service for ticket " + ticketId, ticketId);
            AiTriageResult aiResult = aiTriageAssistant.triage(request.message(), request.allowedTicketTypes());

            validateAiResult(aiResult, request.allowedTicketTypes());

            int urgencyScore = aiResult.urgencyScore();
            int confidence = aiResult.aiConfidencePercent();

            List<Long> relatedTicketIds;
            try {
                eventLogService.addEvent("INFO", "Searching for similar tickets for ticket " + ticketId, ticketId);
                SimilaritySearchResponse similarityResponse = similarityService.searchSimilarTickets(
                        aiResult.ticketType(), request.message(), request.ticketId());
                relatedTicketIds = similarityResponse != null && similarityResponse.relatedTicketIds() != null
                        ? similarityResponse.relatedTicketIds()
                        : List.of();
                eventLogService.addEvent("INFO", "Found " + relatedTicketIds.size() + " similar tickets for ticket " + ticketId, ticketId);
            } catch (Exception e) {
                LOG.warnf("Similarity lookup failed for ticket %s: %s", ticketId, e.getMessage());
                eventLogService.addEvent("WARN", "Similarity lookup failed for ticket " + ticketId + ": " + e.getMessage(), ticketId);
                relatedTicketIds = List.of();
            }

            // Call document service to find related policy/company documents
            // Capture any errors but don't fail the request
            List<DocumentSearchResponse.DocumentResult> policyCitations;
            try {
                eventLogService.addEvent("INFO", "Searching for policy documents for ticket " + ticketId, ticketId);
                DocumentSearchResponse documentResponse = documentService.searchDocuments(request.message());
                policyCitations = (documentResponse != null && documentResponse.results() != null)
                        ? documentResponse.results()
                        : List.of();
                eventLogService.addEvent("INFO", "Found " + policyCitations.size() + " policy documents for ticket " + ticketId, ticketId);
            } catch (Exception e) {
                LOG.warnf("Document lookup failed for ticket %s: %s", ticketId, e.getMessage());
                eventLogService.addEvent("WARN", "Document lookup failed for ticket " + ticketId + ": " + e.getMessage(), ticketId);
                policyCitations = List.of();
            }

            TriageResponse response = new TriageResponse("OK", aiResult.ticketType(), urgencyScore, confidence,
                    relatedTicketIds, policyCitations, null);

            TicketView ticketView = new TicketView(request.ticketId(), request.incomingRequestId(),
                    request.message(), Instant.now(), response.status(), response.ticketType(),
                    response.urgencyScore(), response.aiConfidencePercent(),
                    response.relatedTicketIds(), response.policyCitations(), response.failReason());
            eventLogService.addTicket(ticketView);
            eventLogService.addEvent("INFO", "Successfully processed ticket " + ticketId + " - Type: " + aiResult.ticketType() + ", Urgency: " + urgencyScore, ticketId);

            return response;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("MCP")) {
                LOG.warn("MCP server not responding");
                eventLogService.addEvent("WARN", "MCP server not responding (localhost:9000). Start urgency-mcp-helidon.", ticketId);
            }
            LOG.errorf(e, "Error processing triage request");
            String failReason = extractFailReason(e);

            TriageResponse response = new TriageResponse("FAILED", null, null, null, List.of(), List.of(), failReason);

            eventLogService.addEvent("ERROR", "Failed to process ticket " + ticketId + ": " + failReason, ticketId);

            // Store failed ticket view
            TicketView ticketView = new TicketView(request.ticketId(), request.incomingRequestId(),
                    request.message(), Instant.now(), response.status(), response.ticketType(),
                    response.urgencyScore(), response.aiConfidencePercent(),
                    response.relatedTicketIds(), response.policyCitations(), response.failReason());
            eventLogService.addTicket(ticketView);

            return response;
        }
    }

    @GET
    @Path("/events")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Event> getEvents() {
        return eventLogService.getEvents();
    }

    @GET
    @Path("/tickets")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TicketView> getTickets() {
        return eventLogService.getTickets();
    }

    private void validateAiResult(AiTriageResult result, List<TriageRequest.TicketTypeInfo> allowedTypes) {
        if (result == null) {
            throw new RuntimeException("AI service returned null result");
        }
        if (result.ticketType() == null) {
            throw new RuntimeException("AI service did not return a ticket type");
        }

        List<String> allowedTypeNames = allowedTypes.stream()
                .map(TriageRequest.TicketTypeInfo::type)
                .toList();

        if (!allowedTypeNames.contains(result.ticketType())) {
            throw new RuntimeException("CONTRACT_VIOLATION: AI returned ticket type '" +
                    result.ticketType() + "' which is not in the allowed list: " + allowedTypeNames);
        }

        if (result.urgencyScore() == null || result.urgencyScore() < 1 || result.urgencyScore() > 10) {
            throw new RuntimeException("CONTRACT_VIOLATION: Urgency score must be between 1 and 10, got: " +
                    result.urgencyScore());
        }

        if (result.aiConfidencePercent() == null ||
                result.aiConfidencePercent() < 0 || result.aiConfidencePercent() > 100) {
            throw new RuntimeException("CONTRACT_VIOLATION: Confidence must be between 0 and 100, got: " +
                    result.aiConfidencePercent());
        }
    }

    private String extractFailReason(Exception e) {
        return switch (e.getMessage()) {
            case String message when (message.startsWith("SERVICE_TIMEOUT:")
                    || message.startsWith("PARSE_FAILED:")
                    || message.startsWith("AI_TRIAGE_FAILED:")) -> message;
            case String message when (message.startsWith("CONTRACT_VIOLATION:")) -> "AI_TRIAGE_FAILED: " + message;
            case String message -> "AI_TRIAGE_FAILED: " + e.getClass().getSimpleName() + ", " + message;
        };
    }
}

