package com.example.ticket.service;

import com.example.ticket.domain.constants.EventSeverity;
import com.example.ticket.domain.constants.EventType;
import com.example.ticket.domain.constants.RequestStatus;
import com.example.ticket.domain.constants.TicketType;
import com.example.ticket.domain.model.Ticket;
import com.example.ticket.dto.CreateTicketFromAIDto;
import com.example.ticket.dto.IncomingRequestDto;
import com.example.ticket.dto.TriageRequestDto;
import com.example.ticket.dto.TriageResponseDto;
import com.example.ticket.external.TriageClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logmanager.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class TriageWorkerService {

    private static final Logger LOGGER = Logger.getLogger(TriageWorkerService.class.getName());

    @Inject
    IncomingRequestStateService incomingRequestStateService;

    @Inject
    TicketService ticketService;

    @Inject
    EventService eventService;

    @Inject
    @RestClient
    TriageClient triageClient;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Process a single incoming request asynchronously.
     * Called when a new request is created via the intake endpoint.
     * This method returns immediately - triage happens in background.
     */
    @Transactional
    public void processRequest(IncomingRequestDto request) {
        // Only process NEW requests
        if (request.status() != RequestStatus.NEW) {
            return;
        }

        // Mark as IN_PROGRESS immediately to prevent duplicate processing
        // This must be done synchronously in a transaction
        try {
            incomingRequestStateService.markAsAiTriageInProgress(request.id());
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error marking request as IN_PROGRESS: ", e);
            return;
        }

        // Create placeholder ticket first (triage service requires ticketId)
        Ticket placeholderTicket;
        try {
            placeholderTicket = createPlaceholderTicketForTriage(request.userId(), request.rawText(), request.id());
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error creating placeholder ticket for request " + request.id() + ": ", e);
            return;
        }

        // Emit AI_TRIAGE_STARTED event
        eventService.logEvent(
                EventType.AI_TRIAGE_STARTED,
                EventSeverity.WARNING,
                "ai-triage-worker",
                "AI triage started for request #" + request.id() + " (ticket #" + placeholderTicket.getId() + ")",
                placeholderTicket.getId(),
                request.id(),
                null
        );

        // Build triage request
        List<TriageRequestDto.AllowedTicketType> types = new ArrayList<>();

        types.add(new TriageRequestDto.AllowedTicketType("BILLING_REFUND", "User is asking for a refund or billing reimbursement"));
        types.add(new TriageRequestDto.AllowedTicketType("BILLING_OTHER", "Other billing-related issue requiring human review"));
        types.add(new TriageRequestDto.AllowedTicketType("SCHEDULING_CANCELLATION", "User wants to cancel or reschedule an appointment"));
        types.add(new TriageRequestDto.AllowedTicketType("SCHEDULING_OTHER", "Scheduling-related issue that does not clearly fit cancellation or rescheduling"));
        types.add(new TriageRequestDto.AllowedTicketType("ACCOUNT_ACCESS", "Problems logging in, password reset, or account access"));
        types.add(new TriageRequestDto.AllowedTicketType("SUPPORT_OTHER", "General support question not clearly related to billing or scheduling"));
        types.add(new TriageRequestDto.AllowedTicketType("BUG_APP", "Bug or error in the user-facing application or UI"));
        types.add(new TriageRequestDto.AllowedTicketType("BUG_BACKEND", "Bug or error in backend systems, APIs, or data processing"));
        types.add(new TriageRequestDto.AllowedTicketType("ENGINEERING_OTHER", "Engineering-related issue that does not clearly fit a known bug category"));
        types.add(new TriageRequestDto.AllowedTicketType("OTHER", "AI cannot confidently classify this request and a human dispatcher must decide"));

        TriageRequestDto triageRequest = new TriageRequestDto(request.id(), request.rawText(), placeholderTicket.getId(), types);

        // Store ticketId in final variable for use in async callbacks
        final Long ticketId = placeholderTicket.getId();

        // Call AI triage service asynchronously
        // Completion happens in background thread - never blocks request thread
        triageClient.classifyAsync(triageRequest)
                .thenComposeAsync(response -> {
                    // Process response in background - update placeholder ticket
                    // Use the same executor to ensure proper context
                    return CompletableFuture.runAsync(() -> {
                        try {
                            // Call through self-injected proxy to ensure @Transactional works
                            handleTriageResponse(request, response, ticketId);
                        } catch (Exception e) {
                            LOGGER.log(Level.ERROR, "Error handling triage response for request " + request.id() + ": ", e);
                            // Fallback: update ticket with OTHER type
                            updateTicketAsFallback(ticketId, request, "Error processing triage response: " + e.getMessage());
                        }
                    });
                })
                .exceptionally(throwable -> {
                    // Handle any exception in the async chain
                    LOGGER.log(Level.ERROR, "Error in triage async chain for request " + request.id() + ": ", throwable);
                    updateTicketAsFallback(ticketId, request, "Triage service exception: " + throwable.getMessage());
                    return null;
                });
    }

    @Transactional
    public Ticket createPlaceholderTicketForTriage(String userId, String originalRequest, Long incomingRequestId) {
        return ticketService.createPlaceholderTicketForTriage(userId, originalRequest, incomingRequestId);
    }

    @Transactional
    public void handleTriageResponse(IncomingRequestDto request, TriageResponseDto response, Long ticketId) {
        if ("OK".equals(response.status())) {
            // Success - update placeholder ticket with AI classification
            ticketService.updateTicketWithTriageResults(
                    ticketId,
                    response.ticketType(),
                    response.urgencyScore() != null ? response.urgencyScore().doubleValue() : 5.0,
                    response.aiConfidencePercent() != null ? response.aiConfidencePercent().doubleValue() : 0.0,
                    response.relatedTicketIds(),
                    response.policyCitations()
            );

            // Emit AI_TRIAGE_RESULT event
            Map<String, Object> aiPayload = new HashMap<>();
            aiPayload.put("relatedTicketIds", response.relatedTicketIds() != null ? response.relatedTicketIds() : List.of());
            aiPayload.put("policyCitations", response.policyCitations() != null ? response.policyCitations() : List.of());
            String aiPayloadJson;
            try {
                aiPayloadJson = objectMapper.writeValueAsString(aiPayload);
            } catch (Exception e) {
                aiPayloadJson = "{}";
            }

            eventService.logEvent(
                    EventType.AI_TRIAGE_RESULT,
                    EventSeverity.WARNING,
                    "ai-triage-worker",
                    String.format("AI triage completed for request #%d (ticket #%d): %s (confidence: %d%%, urgency: %d)",
                            request.id(),
                            ticketId,
                            response.ticketType(),
                            response.aiConfidencePercent() != null ? response.aiConfidencePercent() : 0,
                            response.urgencyScore() != null ? response.urgencyScore() : 0),
                    ticketId,
                    request.id(),
                    aiPayloadJson
            );

            // Emit AI_TICKET_CREATED event (ticket is now fully created)
            eventService.logEvent(
                    EventType.AI_TICKET_CREATED,
                    EventSeverity.INFO,
                    "ai-triage-worker",
                    "AI ticket #" + ticketId + " created from incoming request #" + request.id(),
                    ticketId,
                    request.id(),
                    aiPayloadJson
            );
        } else {
            // Failed - update ticket as fallback
            updateTicketAsFallback(ticketId, request, response.failReason() != null ? response.failReason() : "AI triage service returned FAILED status");
        }
    }

    @Transactional
    public void updateTicketAsFallback(Long ticketId, IncomingRequestDto request, String failReason) {
        // Update placeholder ticket with OTHER type for dispatcher review
        ticketService.updateTicketWithTriageResults(
                ticketId,
                TicketType.OTHER.name(), // Maps to DISPATCH team
                5.0,
                0.0, // Zero confidence for fallback
                List.of(),
                List.of()
        );

        // Store failure reason in AI payload
        Map<String, Object> aiPayload = new HashMap<>();
        aiPayload.put("failReason", failReason);
        aiPayload.put("relatedTicketIds", List.of());
        aiPayload.put("policyCitations", List.of());
        String aiPayloadJson;
        try {
            aiPayloadJson = objectMapper.writeValueAsString(aiPayload);
        } catch (Exception e) {
            aiPayloadJson = "{\"failReason\":\"" + failReason.replace("\"", "\\\"") + "\"}";
        }

        // Update ticket's AI payload with failure reason
        // Note: This is a simplified approach - in a real system you might want to update the ticket's aiPayloadJson field
        // For now, the failure reason is logged in the event

        // Emit AI_TRIAGE_FAILED event
        eventService.logEvent(
                EventType.AI_TRIAGE_FAILED,
                EventSeverity.ERROR,
                "ai-triage-worker",
                "AI triage failed for request #" + request.id() + " (ticket #" + ticketId + "): " + failReason,
                ticketId,
                request.id(),
                aiPayloadJson
        );
    }

    @Transactional
    public void createFallbackTicket(IncomingRequestDto request, String failReason) {
        // Legacy method - kept for backward compatibility
        // This should not be called in the new flow, but kept for safety

        // Store failure reason in AI payload
        Map<String, Object> aiPayload = new HashMap<>();
        aiPayload.put("failReason", failReason);
        aiPayload.put("relatedTicketIds", List.of());
        aiPayload.put("policyCitations", List.of());
        String aiPayloadJson;
        try {
            aiPayloadJson = objectMapper.writeValueAsString(aiPayload);
        } catch (Exception e) {
            aiPayloadJson = "{\"failReason\":\"" + failReason.replace("\"", "\\\"") + "\"}";
        }

        CreateTicketFromAIDto aiDto = new CreateTicketFromAIDto(request.userId(), request.rawText(), TicketType.OTHER,
                5.0, false, 0.0,
                aiPayloadJson, request.id());
        ticketService.createTicketFromAi(aiDto);

        // Emit AI_TRIAGE_FAILED event
        eventService.logEvent(
                EventType.AI_TRIAGE_FAILED,
                EventSeverity.ERROR,
                "ai-triage-worker",
                "AI triage failed for request #" + request.id() + ": " + failReason,
                null,
                request.id(),
                aiDto.aiPayloadJson()
        );
    }

    /**
     * Process all NEW requests (for manual trigger via API endpoint).
     * Not used in normal flow - requests are processed immediately when created.
     * This method processes requests asynchronously using the AI triage client.
     */
    public void processNewRequests() {
        // Fetch only NEW requests (AI_TRIAGE_IN_PROGRESS are being processed, skip those)
        List<IncomingRequestDto> newRequests = incomingRequestStateService.getIncomingRequests(RequestStatus.NEW);

        for (IncomingRequestDto request : newRequests) {
            // Process each request asynchronously (non-blocking)
            processRequest(request);
        }
    }

}
