package com.example.ticket.service;

import com.example.ticket.domain.constants.*;
import com.example.ticket.domain.model.Ticket;
import com.example.ticket.domain.model.Comment;
import com.example.ticket.dto.*;
import com.example.ticket.external.SimilarityClient;
import com.example.ticket.mapper.TicketMapper;
import com.example.ticket.mapper.TicketTypeTeamMapper;
import com.example.ticket.service.adapter.CommentService;
import com.example.ticket.service.adapter.EventService;
import com.example.ticket.service.adapter.IncomingRequestService;
import com.example.ticket.service.adapter.TicketStateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TicketService {

    @Inject
    TicketStateService ticketStateService;

    @Inject
    CommentService commentService;

    @Inject
    EventService eventService;

    @Context
    ContainerRequestContext requestContext;

    @Inject
    @RestClient
    SimilarityClient similarityClient;

    @Inject
    IncomingRequestService incomingRequestService;

    @Transactional
    public TicketDto submit(DispatchedTicketDto dto) {
        // Get the original request text
        var incomingRequest = incomingRequestService.getIncomingRequest(dto.incomingRequestId());
        if (incomingRequest == null) {
            throw new IllegalArgumentException("Incoming request not found: " + dto.incomingRequestId());
        }

        // Validate ticketType
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        // TicketType defines intent; AssignedTeam is a derived consequence.
        Long nextId = ticketStateService.findMaxId() + 1;
        TicketMapper ticketMapper = new TicketMapper();
        Ticket ticket = ticketMapper.toDispatchTicket(dto, incomingRequest, nextId);
        ticketStateService.persist(ticket);

        eventService.logEvent(
                EventType.DISPATCH_SUBMITTED,
                EventSeverity.INFO,
                "dispatch-ui",
                "Ticket #" + ticket.getId() + " dispatched by " + dto.dispatcherId() + (dto.incomingRequestId() != null ? " (from request #" + dto.incomingRequestId() + ")" : ""),
                ticket.getId(),
                dto.incomingRequestId(),
                null
        );

        eventService.logEvent(
                EventType.TICKET_CREATED,
                EventSeverity.INFO,
                "ticketing-api",
                "Ticket #" + ticket.getId() + " created from dispatch" + (dto.incomingRequestId() != null ? " (request #" + dto.incomingRequestId() + ")" : ""),
                ticket.getId(),
                dto.incomingRequestId(),
                null
        );

        // Send to similarity service (async, non-blocking)
        sendTicketToSimilarityService(ticket);

        TicketDto ticketDto = ticketMapper.toTicketDto(ticket);

        // Mark incoming request as converted
        incomingRequestService.markAsConvertedToTicket(dto.incomingRequestId());

        return ticketDto;
    }

    @Transactional
    public TicketDto manualSubmit(ManualTicketDto dto) {
        // Validate ticketType
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        TicketMapper ticketMapper =  new TicketMapper();
        Long nextId = ticketStateService.findMaxId() + 1;
        Ticket ticket = ticketMapper.toManualTicket(dto, nextId);
        ticketStateService.persist(ticket);

        eventService.logEvent(
                EventType.TICKET_CREATED,
                EventSeverity.INFO,
                "ticketing-api",
                "Ticket #" + ticket.getId() + " created via API endpoint",
                ticket.getId(),
                null,
                null
        );

        // Send to similarity service (async, non-blocking)
        sendTicketToSimilarityService(ticket);

        return ticketMapper.toTicketDto(ticket);
    }

    @Transactional
    public TicketDto aiSubmit(AITicketDto dto) {
        // Validate ticketType - AI can only suggest ticketType
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null. AI must provide a valid ticketType.");
        }

        // TicketType defines intent; AssignedTeam is a derived consequence.
        TicketMapper ticketMapper = new TicketMapper();
        Long nextId = ticketStateService.findMaxId() + 1;
        Ticket ticket = ticketMapper.toAiTicket(dto, nextId);
        ticketStateService.persist(ticket);

        // If this ticket was created from an incoming request, mark it as converted
        if (dto.incomingRequestId() != null) {
            incomingRequestService.markAsConvertedToTicket(dto.incomingRequestId());
        }

        eventService.logEvent(
                EventType.AI_TICKET_CREATED,
                EventSeverity.INFO,
                "ai-triage-worker",
                "AI ticket #" + ticket.getId() + " created from incoming request #" + (dto.incomingRequestId() != null ? dto.incomingRequestId() : "N/A"),
                ticket.getId(),
                dto.incomingRequestId(),
                dto.aiPayloadJson()
        );

        // Send to similarity service (async, non-blocking)
        sendTicketToSimilarityService(ticket);

        return ticketMapper.toTicketDto(ticket);
    }

    public List<TicketDto> findTickets(String view, String team, String user) {
        List<Ticket> tickets = switch (view) {
            case "team" -> ticketStateService.findByAssignedTeam(team);
            case String v when (user != null && v.equals("mine")) -> ticketStateService.findByAssignedTo(user);
            case String _ -> ticketStateService.listAll();
        };

        // Filter out ROLLED_BACK tickets from normal views
        tickets = tickets.stream()
                .filter(t -> t.getStatus() != TicketStatus.ROLLED_BACK)
                .toList();
        return tickets.stream()
                .map(ticket -> new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                        ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                        ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                        ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                        filterAiPayloadForCurrentActor(ticket.getAiPayloadJson()), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                        ticket.getUpdatedAt(), List.of()))
                .toList();
    }

    public TicketDto findTicket(Long id) {
        Ticket ticket = ticketStateService.findById(id);
        if (ticket == null) {
            return null;
        }
        List<Comment> comments = commentService.findByTicketId(id);
        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                filterAiPayloadForCurrentActor(ticket.getAiPayloadJson()), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), comments.stream()
                .map(c -> new CommentDto(c.id, ticket.getId(), c.getAuthorId(), c.getBody()))
                .toList());
    }

    @Transactional
    public TicketDto acceptTicket(Long id, String userId) {
        Ticket ticket = ticketStateService.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.setStatus(TicketStatus.TRIAGED);
        ticket.setAssignedTo(userId);
        ticketStateService.persist(ticket);

        eventService.logEvent(
                EventType.TICKET_ACCEPTED,
                EventSeverity.INFO,
                "ticketing-ui",
                "Ticket #" + ticket.getId() + " accepted by " + userId,
                ticket.getId(),
                null,
                null
        );

        return new TicketMapper().toTicketDto(ticket);
    }

    @Transactional
    public TicketDto rejectAndReturnToDispatch(Long id) {
        Ticket ticket = ticketStateService.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.setStatus(TicketStatus.RETURNED_TO_DISPATCH);
        ticket.setAssignedTeam("DISPATCH");
        ticket.setAssignedTo(null);
        ticketStateService.persist(ticket);

        eventService.logEvent(
                EventType.RETURNED_TO_DISPATCH,
                EventSeverity.INFO,
                "ticketing-ui",
                "Ticket #" + ticket.getId() + " returned to dispatch",
                ticket.getId(),
                null,
                null
        );

        return new TicketMapper().toTicketDto(ticket);
    }

    @Transactional
    public TicketDto updateTicketStatus(Long id, TicketStatus status) {
        Ticket ticket = ticketStateService.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.setStatus(status);
        ticketStateService.persist(ticket);

        eventService.logEvent(
                EventType.TICKET_STATUS_CHANGED,
                EventSeverity.INFO,
                "ticketing-ui",
                "Ticket #" + ticket.getId() + " status changed to " + status,
                ticket.getId(),
                null,
                null
        );

        return new TicketMapper().toTicketDto(ticket);
    }

    /**
     * Updates ticketType and automatically recomputes assignedTeam.
     * Dispatcher may change ticketType (not team); team updates automatically.
     *
     * @param id  Ticket ID
     * @param dto UpdateTicketTypeDto with new ticketType
     * @return Updated ticket DTO
     */
    @Transactional
    public TicketDto updateTicketType(Long id, UpdateTicketTypeDto dto) {
        // Validate ticketType
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        Ticket ticket = ticketStateService.findById(id);
        if (ticket == null) {
            return null;
        }

        TicketType oldType = ticket.getTicketType();
        ticket.setTicketType(dto.ticketType());
        // TicketType defines intent; AssignedTeam is a derived consequence.
        // Changing ticketType always recomputes assignedTeam.
        TicketMapper ticketMapper = new TicketMapper();
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        ticket.setAssignedTeam(ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType()).name());
        // Re-assign to default user for the new team if not already assigned
        if (ticket.getAssignedTo() == null) {
            String assignedTo = ticketMapper.mapUserIdForTeam(ticket.getAssignedTeam());
            ticket.setAssignedTo(assignedTo);
        }
        ticketStateService.persist(ticket);

        eventService.logEvent(
                EventType.TICKET_TYPE_CHANGED,
                EventSeverity.INFO,
                "ticketing-ui",
                "Ticket #" + ticket.getId() + " type changed from " + oldType + " to " + dto.ticketType() + " (team: " + ticket.getAssignedTeam() + ")",
                ticket.getId(),
                null,
                null
        );

        return ticketMapper.toTicketDto(ticket);
    }

    @Transactional
    public CommentDto addComment(Long ticketId, AddCommentDto dto) {
        Ticket ticket = ticketStateService.findById(ticketId);
        if (ticket == null) {
            return null;
        }
        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthorId(dto.authorId());
        comment.setBody(dto.body());
        commentService.persist(comment);

        eventService.logEvent(
                EventType.TICKET_COMMENT_ADDED,
                EventSeverity.INFO,
                "ticketing-ui",
                "Comment added to ticket #" + ticketId + " by " + dto.authorId(),
                ticketId,
                null,
                null
        );

        return new CommentDto(comment.id, comment.getTicket().getId(), comment.getAuthorId(), comment.getBody());
    }

    @Transactional
    public TicketDto rollbackTicket(Long ticketId) {
        Ticket ticket = ticketStateService.findById(ticketId);

        if (ticket == null) {
            return null;
        }

        // Verify this is an AI ticket that can be rolled back
        if (ticket.getStatus() != TicketStatus.FROM_AI || !ticket.getRollbackAllowed()) {
            throw new IllegalArgumentException("Ticket cannot be rolled back. Only FROM_AI tickets with rollbackAllowed=true can be rolled back.");
        }

        if (ticket.getIncomingRequestId() == null) {
            throw new IllegalArgumentException("Ticket has no associated incoming request to roll back to.");
        }

        // Mark ticket as rolled back
        ticket.setStatus(TicketStatus.ROLLED_BACK);
        ticketStateService.persist(ticket);

        // Mark incoming request as returned from AI
        incomingRequestService.markAsReturnedFromAi(ticket.getIncomingRequestId());

        // Emit event
        eventService.logEvent(
                EventType.AI_TICKET_ROLLED_BACK,
                EventSeverity.INFO,
                "ticketing-ui",
                "AI ticket #" + ticket.getId() + " rolled back to incoming request #" + ticket.getIncomingRequestId(),
                ticket.getId(),
                ticket.getIncomingRequestId(),
                null
        );

        return new TicketMapper().toTicketDto(ticket);
    }

    /**
     * Create a placeholder ticket for triage processing.
     * This ticket will be updated with triage results later.
     */
    @Transactional
    public Ticket createPlaceholderTicketForTriage(String userId, String originalRequest, Long incomingRequestId) {
        TicketMapper ticketMapper = new TicketMapper();
        Ticket ticket = ticketMapper.toPlaceholderTicket(userId, originalRequest, incomingRequestId);
        ticketStateService.persist(ticket);

        // Send to similarity service when ticket is created (text is final and won't change)
        // The ticketType will be updated later, but the embedding is based on the text, not the type
        sendTicketToSimilarityService(ticket);

        return ticket;
    }

    /**
     * Filter AI payload (policyCitations) based on the current actor's team.
     * This is applied at read-time so DB always stores the full payload.
     */
    private String filterAiPayloadForCurrentActor(String aiPayloadJson) {
        if (aiPayloadJson == null || aiPayloadJson.isBlank()) {
            return aiPayloadJson;
        }
        String actorTeam;
        try {
            if (requestContext == null) return "DISPATCHER";
            String team = requestContext.getHeaderString("X-Actor-Team");
            actorTeam = (team != null) ? team : "DISPATCHER";
        } catch (Exception e) {
            // If no request context is active, fall back to unfiltered payload
            return aiPayloadJson;
        }
        if (actorTeam.isBlank()) {
            return aiPayloadJson;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(aiPayloadJson);
            JsonNode citationsNode = root.get("policyCitations");
            if (citationsNode == null || !citationsNode.isArray()) {
                return aiPayloadJson;
            }

            String normalizedTeam = actorTeam.toLowerCase();
            ArrayNode filtered = objectMapper.createArrayNode();
            for (JsonNode citation : citationsNode) {
                if (citation == null || !citation.isObject()) continue;
                JsonNode rbacTeamsNode = citation.get("rbacTeams");
                if (rbacTeamsNode == null || !rbacTeamsNode.isArray()) continue;
                boolean hasAccess = false;
                for (JsonNode teamNode : rbacTeamsNode) {
                    if (teamNode != null && teamNode.isTextual()) {
                        String team = teamNode.asText();
                        if (team != null && team.equalsIgnoreCase(normalizedTeam)) {
                            hasAccess = true;
                            break;
                        }
                    }
                }
                if (hasAccess) {
                    filtered.add(citation);
                }
            }

            if (root.isObject()) {
                ((ObjectNode) root).set("policyCitations", filtered);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            System.err.println("Error filtering AI payload for actor team: " + e.getMessage());
            return aiPayloadJson;
        }
    }

    /**
     * Update a placeholder ticket with triage results.
     */
    @Transactional
    public void updateTicketWithTriageResults(Long ticketId, String ticketType, Double urgencyScore, Double aiConfidence,
                                              List<Long> relatedTicketIds, List<TriageResponseDto.PolicyCitation> policyCitations) {
        Ticket ticket = ticketStateService.findById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found: " + ticketId);
        }

        ticket.setTicketType(TicketType.valueOf(ticketType));
        ticket.setUrgencyScore(urgencyScore != null ? urgencyScore : 5.0);
        ticket.setUrgencyFlag(ticket.getUrgencyScore() >= 7.0); // Flag as urgent if score >= 7
        ticket.setAiConfidence(aiConfidence != null ? aiConfidence / 100.0 : 0.0);

        // Update assigned team based on new ticket type
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        ticket.setAssignedTeam(ticketTypeTeamMapper.deriveTeamFromTicketType(ticket.getTicketType()).name());
        TicketMapper ticketMapper = new TicketMapper();
        String assignedTo = ticketMapper.mapUserIdForTeam(ticket.getAssignedTeam());
        ticket.setAssignedTo(assignedTo);

        // Store relatedTicketIds and policyCitations in aiPayloadJson
        Map<String, Object> aiPayload = new HashMap<>();
        aiPayload.put("relatedTicketIds", relatedTicketIds != null ? relatedTicketIds : List.of());
        aiPayload.put("policyCitations", policyCitations != null ? policyCitations : List.of());
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ticket.setAiPayloadJson(objectMapper.writeValueAsString(aiPayload));
        } catch (Exception e) {
            System.err.println("Error serializing AI payload: " + e.getMessage());
            ticket.setAiPayloadJson("{}");
        }

        ticketStateService.persist(ticket);

        // Mark incoming request as converted if it exists
        if (ticket.getIncomingRequestId() != null) {
            incomingRequestService.markAsConvertedToTicket(ticket.getIncomingRequestId());
        }

        // Do NOT send to similarity service here - this is an UPDATE operation.
        // The ticket was already sent when it was created as a placeholder.
    }

    /**
     * Send ticket to similarity service for embedding creation.
     * This is called only when tickets are created (text is immutable after creation).
     * Non-blocking: errors are logged but don't affect ticket creation.
     */
    private void sendTicketToSimilarityService(Ticket ticket) {
        SimilarityUpsertRequestDto request = new SimilarityUpsertRequestDto(ticket.getId(), ticket.getTicketType().name(), ticket.getOriginalRequest());

        similarityClient.upsertTicketAsync(request)
                .thenAccept(_ -> {
                    // Success - log event
                    eventService.logEvent(
                            EventType.SYSTEM_STEP,
                            EventSeverity.INFO,
                            "ticketing-api",
                            "Sending ticket #" + ticket.getId() + " to embedding service",
                            ticket.getId(),
                            null,
                            null
                    );
                })
                .exceptionally(throwable -> {
                    // Error - log event with error details
                    String errorMessage = throwable.getMessage();
                    if (errorMessage == null) {
                        errorMessage = throwable.getClass().getSimpleName();
                    }
                    eventService.logEvent(
                            EventType.ERROR_OCCURRED,
                            EventSeverity.WARNING,
                            "ticketing-api",
                            "sent to ticket similarity system: error - " + throwable.getClass().getSimpleName() + " - " + errorMessage,
                            ticket.getId(),
                            null,
                            null
                    );
                    return null;
                });
    }

    @Transactional
    public void updateTicketAsFallback(Long ticketId, IncomingRequestDto request, String failReason) {
        // Update placeholder ticket with OTHER type for dispatcher review
        updateTicketWithTriageResults(
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
            ObjectMapper objectMapper = new ObjectMapper();
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

}
