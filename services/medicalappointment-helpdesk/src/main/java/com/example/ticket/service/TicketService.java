package com.example.ticket.service;

import com.example.ticket.config.ActorContext;
import com.example.ticket.domain.constants.*;
import com.example.ticket.domain.model.Ticket;
import com.example.ticket.domain.model.TicketComment;
import com.example.ticket.dto.*;
import com.example.ticket.external.SimilarityClient;
import com.example.ticket.mapper.TicketTypeTeamMapper;
import com.example.ticket.persistence.CommentRepository;
import com.example.ticket.persistence.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TicketService {

    @Inject
    TicketRepository ticketRepository;

    @Inject
    CommentRepository commentRepository;

    @Inject
    EventService eventService;

    @Inject
    ActorContext actorContext;

    @Inject
    @RestClient
    SimilarityClient similarityClient;

    @Inject
    IncomingRequestService incomingRequestService;

    @Transactional
    public TicketDto createTicketFromDispatch(DispatchCreateTicketDto dto, String originalRequest, String requestUserId) {
        // Validate ticketType
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        Ticket ticket = new Ticket();
        ticket.setUserId(requestUserId); // Get from incoming request
        ticket.setOriginalRequest(originalRequest);
        ticket.setTicketType(dto.ticketType());
        ticket.setStatus(TicketStatus.FROM_DISPATCH);
        ticket.setSource(TicketSource.MANUAL);
        // TicketType defines intent; AssignedTeam is a derived consequence.
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        ticket.setAssignedTeam(ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType()).name());
        // Auto-assign to default user for the team
        ticket.setAssignedTo(mapUserIdForTeam(ticket.getAssignedTeam()));
        ticket.setUrgencyFlag(dto.urgencyFlag() != null ? dto.urgencyFlag() : false);
        ticket.setUrgencyScore(dto.urgencyScore());
        ticket.setRollbackAllowed(false);
        ticket.setIncomingRequestId(dto.incomingRequestId());
        ticketRepository.persist(ticket);

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

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
    }

    @Transactional
    public TicketDto createTicketManual(CreateTicketManualDto dto) {
        // Validate ticketType
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        Ticket ticket = new Ticket();
        ticket.setUserId(dto.userId());
        ticket.setOriginalRequest(dto.originalRequest());
        ticket.setTicketType(dto.ticketType());
        ticket.setStatus(dto.status() != null ? dto.status() : TicketStatus.FROM_DISPATCH);
        ticket.setSource(TicketSource.MANUAL);
        // TicketType defines intent; AssignedTeam is a derived consequence.
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        Team assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType());
        ticket.setAssignedTeam(assignedTeam.name());
        // Auto-assign to default user if not specified
        ticket.setAssignedTo(dto.assignedTo() != null ? dto.assignedTo() : mapUserIdForTeam(ticket.getAssignedTeam()));
        ticket.setUrgencyFlag(dto.urgencyFlag() != null ? dto.urgencyFlag() : false);
        ticket.setUrgencyScore(dto.urgencyScore());
        ticket.setRollbackAllowed(false);
        ticketRepository.persist(ticket);

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

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
    }

    @Transactional
    public TicketDto createTicketFromAi(CreateTicketFromAIDto dto) {
        // Validate ticketType - AI can only suggest ticketType
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null. AI must provide a valid ticketType.");
        }

        Ticket ticket = new Ticket();
        ticket.setId(ticketRepository.findMaxId() + 1);
        ticket.setUserId(dto.userId());
        ticket.setOriginalRequest(dto.originalRequest());
        ticket.setTicketType(dto.ticketType());
        ticket.setStatus(TicketStatus.FROM_AI);
        ticket.setSource(TicketSource.AI);
        // TicketType defines intent; AssignedTeam is a derived consequence.
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        ticket.setAssignedTeam(ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType()).name());
        // Auto-assign to default user for the team
        ticket.setAssignedTo(mapUserIdForTeam(ticket.getAssignedTeam()));
        ticket.setUrgencyFlag(dto.urgencyFlag());
        ticket.setUrgencyScore(dto.urgencyScore());
        ticket.setAiConfidence(dto.aiConfidence());
        ticket.setRollbackAllowed(true);
        ticket.setAiPayloadJson(dto.aiPayloadJson());
        ticket.setIncomingRequestId(dto.incomingRequestId());
        ticketRepository.persist(ticket);

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

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
    }

    public List<TicketDto> findTickets(String view, String team, String user) {
        List<Ticket> tickets = switch (view) {
            case "team" -> ticketRepository.findByAssignedTeam(team);
            case String v when (user != null && v.equals("mine")) -> ticketRepository.findByAssignedTo(user);
            case String _ -> ticketRepository.listAll();
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
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }
        List<TicketComment> comments = commentRepository.findByTicketId(id);
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
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.setStatus(TicketStatus.TRIAGED);
        ticket.setAssignedTo(userId);
        ticketRepository.persist(ticket);

        eventService.logEvent(
                EventType.TICKET_ACCEPTED,
                EventSeverity.INFO,
                "ticketing-ui",
                "Ticket #" + ticket.getId() + " accepted by " + userId,
                ticket.getId(),
                null,
                null
        );

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
    }

    @Transactional
    public TicketDto rejectAndReturnToDispatch(Long id) {
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.setStatus(TicketStatus.RETURNED_TO_DISPATCH);
        ticket.setAssignedTeam("DISPATCH");
        ticket.setAssignedTo(null);
        ticketRepository.persist(ticket);

        eventService.logEvent(
                EventType.RETURNED_TO_DISPATCH,
                EventSeverity.INFO,
                "ticketing-ui",
                "Ticket #" + ticket.getId() + " returned to dispatch",
                ticket.getId(),
                null,
                null
        );

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
    }

    @Transactional
    public TicketDto updateTicketStatus(Long id, TicketStatus status) {
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.setStatus(status);
        ticketRepository.persist(ticket);

        eventService.logEvent(
                EventType.TICKET_STATUS_CHANGED,
                EventSeverity.INFO,
                "ticketing-ui",
                "Ticket #" + ticket.getId() + " status changed to " + status,
                ticket.getId(),
                null,
                null
        );

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
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
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }

        // Validate ticketType
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        TicketType oldType = ticket.getTicketType();
        ticket.setTicketType(dto.ticketType());
        // TicketType defines intent; AssignedTeam is a derived consequence.
        // Changing ticketType always recomputes assignedTeam.
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        ticket.setAssignedTeam(ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType()).name());
        // Re-assign to default user for the new team if not already assigned
        if (ticket.getAssignedTo() == null) {
            ticket.setAssignedTo(mapUserIdForTeam(ticket.getAssignedTeam()));
        }
        ticketRepository.persist(ticket);

        eventService.logEvent(
                EventType.TICKET_TYPE_CHANGED,
                EventSeverity.INFO,
                "ticketing-ui",
                "Ticket #" + ticket.getId() + " type changed from " + oldType + " to " + dto.ticketType() + " (team: " + ticket.getAssignedTeam() + ")",
                ticket.getId(),
                null,
                null
        );

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
    }

    @Transactional
    public CommentDto addComment(Long ticketId, AddCommentDto dto) {
        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            return null;
        }
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthorId(dto.authorId());
        comment.setBody(dto.body());
        commentRepository.persist(comment);

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
    public TicketDto rollbackToRequest(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId);

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
        ticketRepository.persist(ticket);

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

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
    }

    /**
     * Create a placeholder ticket for triage processing.
     * This ticket will be updated with triage results later.
     */
    @Transactional
    public Ticket createPlaceholderTicketForTriage(String userId, String originalRequest, Long incomingRequestId) {
        Ticket ticket = new Ticket();
        ticket.setUserId(userId);
        ticket.setOriginalRequest(originalRequest);
        ticket.setTicketType(TicketType.OTHER); // Placeholder - will be updated by triage
        ticket.setStatus(TicketStatus.FROM_AI); // Will be updated after triage
        ticket.setSource(TicketSource.AI);
        ticket.setAssignedTeam("DISPATCH"); // Default team until triage completes
        ticket.setUrgencyFlag(false);
        ticket.setRollbackAllowed(true);
        ticket.setAiPayloadJson("{}");
        ticket.setIncomingRequestId(incomingRequestId);
        ticketRepository.persist(ticket);

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
            actorTeam = actorContext.getActorTeam();
        } catch (Exception e) {
            // If no request context is active, fall back to unfiltered payload
            return aiPayloadJson;
        }
        if (actorTeam == null || actorTeam.isBlank()) {
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
        Ticket ticket = ticketRepository.findById(ticketId);
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
        ticket.setAssignedTo(mapUserIdForTeam(ticket.getAssignedTeam()));

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

        ticketRepository.persist(ticket);

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

    private static String mapUserIdForTeam(String team) {
        if (team == null) return "demo-user";

        String teamLower = team.toLowerCase();
        return switch (teamLower) {
            case "dispatch" -> "dispatch-user1";
            case "billing" -> "billing-user1";
            case "reschedule" -> "reschedule-user1";
            case "engineering" -> "engineering-user1";
            case String _ -> teamLower + "-user1";
        };
    }
}
