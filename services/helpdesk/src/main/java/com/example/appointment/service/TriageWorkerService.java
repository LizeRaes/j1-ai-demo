package com.example.appointment.service;

import com.example.appointment.domain.constants.EventSeverity;
import com.example.appointment.domain.constants.EventType;
import com.example.appointment.domain.constants.RequestStatus;
import com.example.appointment.domain.model.Ticket;
import com.example.appointment.dto.*;
import com.example.appointment.external.TriageClient;
import com.example.appointment.service.adapter.EventService;
import com.example.appointment.service.adapter.IncomingRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logmanager.Level;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
public class TriageWorkerService {

    private static final Logger LOGGER = Logger.getLogger(TriageWorkerService.class.getName());

    @Inject
    IncomingRequestService incomingRequestService;

    @Inject
    TicketService ticketService;

    @Inject
    EventService eventService;

    @Inject
    @RestClient
    TriageClient triageClient;

    public IncomingRequestDto createIncomingRequest(CreateIncomingRequestDto dto) {
        IncomingRequestDto request = incomingRequestService.createIncomingRequest(dto);

        String eventSource = dto.channel() != null && !dto.channel().isEmpty()
                ? dto.channel()
                : "ticketing-api";

        eventService.logEvent(
                EventType.INCOMING_REQUEST_RECEIVED,
                EventSeverity.INFO,
                eventSource,
                "Incoming request #" + request.id() + " received from user " + dto.userId(),
                null,
                request.id(),
                null
        );

        try {
            processRequest(request);
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Error in triage worker: ", e);
        }

        return request;
    }

    /**
     * Process a single incoming request asynchronously.
     * Called when a new request is created via the intake endpoint.
     * This method returns immediately - triage happens in background.
     */
    public void processRequest(IncomingRequestDto request) {
        // Only process NEW requests
        if (request.status() != RequestStatus.NEW) {
            return;
        }

        // Mark as IN_PROGRESS immediately to prevent duplicate processing
        // This must be done synchronously in a transaction
        try {
            incomingRequestService.markAsAiTriageInProgress(request.id());
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error marking request as IN_PROGRESS: ", e);
            return;
        }

        // Create initial ticket first so triage receives a real ticket ID.
        Ticket initialTicket;
        try {
            initialTicket = ticketService.createInitialTicketForTriage(request.userId(), request.rawText(), request.id());
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Error creating initial triage ticket for request " + request.id() + ": ", e);
            incomingRequestService.markAsAiTriageFailed(request.id());
            return;
        }

        // Emit AI_TRIAGE_STARTED event
        eventService.logEvent(
                EventType.AI_TRIAGE_STARTED,
                EventSeverity.WARNING,
                "ai-triage-worker",
                "AI triage started for request #" + request.id() + " (ticket #" + initialTicket.getId() + ")",
                initialTicket.getId(),
                request.id(),
                null
        );

        // Build triage request
        TriageRequestDto triageRequest = getTriageRequestDto(request, initialTicket);

        // Store ticketId in final variable for use in async callbacks
        final Long ticketId = initialTicket.getId();

        // Call AI triage service asynchronously
        // Completion happens in background thread - never blocks request thread
        triageClient.classifyAsync(triageRequest)
                .thenAccept(response -> {
                    try {
                        handleTriageResponse(request, response, ticketId);
                    } catch (Exception e) {
                        LOGGER.log(Level.ERROR, "Error handling triage response for request " + request.id() + ": ", e);
                        ticketService.updateTicketAsFallback(ticketId, request, "Error processing triage response: " + e.getMessage());
                    }
                })
                .exceptionally(throwable -> {
                    // Handle any exception in the async chain
                    LOGGER.log(Level.ERROR, "Error in triage async chain for request " + request.id() + ": ", throwable);
                    ticketService.updateTicketAsFallback(ticketId, request, "Triage service exception: " + throwable.getMessage());
                    return null;
                });
    }

    private static @NonNull TriageRequestDto getTriageRequestDto(IncomingRequestDto request, Ticket initialTicket) {
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

        return new TriageRequestDto(request.id(), request.rawText(), initialTicket.getId(), types);
    }

    @Transactional
    public void handleTriageResponse(IncomingRequestDto request, TriageResponseDto response, Long ticketId) {
        if ("OK".equals(response.status())) {
            // Success - update initial triage ticket with AI classification
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
                ObjectMapper objectMapper = new ObjectMapper();
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
            ticketService.updateTicketAsFallback(ticketId, request, response.failReason() != null ? response.failReason() : "AI triage service returned FAILED status");
        }
    }



    /**
     * Process all NEW requests (for manual trigger via API endpoint).
     * Not used in normal flow - requests are processed immediately when created.
     * This method processes requests asynchronously using the AI triage client.
     */
    public void processNewRequests() {
        // Fetch only NEW requests (AI_TRIAGE_IN_PROGRESS are being processed, skip those)
        List<IncomingRequestDto> newRequests = incomingRequestService.getIncomingRequests(RequestStatus.NEW);

        for (IncomingRequestDto request : newRequests) {
            // Process each request asynchronously (non-blocking)
            processRequest(request);
        }
    }

}
