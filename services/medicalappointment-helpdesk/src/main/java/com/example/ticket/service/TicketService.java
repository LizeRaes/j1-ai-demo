package com.example.ticket.service;

import com.example.ticket.config.ActorContext;
import com.example.ticket.config.TicketTypeTeamMapper;
import com.example.ticket.domain.enums.EventSeverity;
import com.example.ticket.domain.enums.EventType;
import com.example.ticket.domain.enums.TicketSource;
import com.example.ticket.domain.enums.TicketStatus;
import com.example.ticket.domain.enums.TicketType;
import com.example.ticket.domain.model.Ticket;
import com.example.ticket.domain.model.TicketComment;
import com.example.ticket.dto.*;
import com.example.ticket.dto.TriageResponseDto;
import com.example.ticket.mapper.TicketMapper;
import com.example.ticket.persistence.CommentRepository;
import com.example.ticket.persistence.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class TicketService {
    @Inject
    TicketRepository ticketRepository;

    @Inject
    CommentRepository commentRepository;

    @Inject
    TicketMapper ticketMapper;

    @Inject
    EventService eventService;

    @Inject
    ActorContext actorContext;
    
    @Inject
    com.example.ticket.config.TeamUserMapper teamUserMapper;

    @Inject
    TicketTypeTeamMapper ticketTypeTeamMapper;

    @Inject
    SimilarityServiceClient similarityServiceClient;
    
    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public TicketDto createTicketFromDispatch(DispatchCreateTicketDto dto, String originalRequest, String requestUserId) {
        // Validate ticketType
        if (dto.ticketType == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }
        
        Ticket ticket = new Ticket();
        ticket.userId = requestUserId; // Get from incoming request
        ticket.originalRequest = originalRequest;
        ticket.ticketType = dto.ticketType;
        ticket.status = TicketStatus.FROM_DISPATCH;
        ticket.source = TicketSource.MANUAL;
        // TicketType defines intent; AssignedTeam is a derived consequence.
        ticket.assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType).name();
        // Auto-assign to default user for the team
        ticket.assignedTo = teamUserMapper.getDefaultUserIdForTeam(ticket.assignedTeam);
        ticket.urgencyFlag = dto.urgencyFlag != null ? dto.urgencyFlag : false;
        ticket.urgencyScore = dto.urgencyScore;
        ticket.rollbackAllowed = false;
        ticket.incomingRequestId = dto.incomingRequestId;
        ticketRepository.persist(ticket);

        eventService.logEvent(
            EventType.DISPATCH_SUBMITTED,
            EventSeverity.INFO,
            "dispatch-ui",
            "Ticket #" + ticket.id + " dispatched by " + dto.dispatcherId + (dto.incomingRequestId != null ? " (from request #" + dto.incomingRequestId + ")" : ""),
            ticket.id,
            dto.incomingRequestId,
            null
        );

        eventService.logEvent(
            EventType.TICKET_CREATED,
            EventSeverity.INFO,
            "ticketing-api",
            "Ticket #" + ticket.id + " created from dispatch" + (dto.incomingRequestId != null ? " (request #" + dto.incomingRequestId + ")" : ""),
            ticket.id,
            dto.incomingRequestId,
            null
        );

        // Send to similarity service (async, non-blocking)
        sendTicketToSimilarityService(ticket);

        return ticketMapper.toDto(ticket);
    }

    @Transactional
    public TicketDto createTicketManual(CreateTicketManualDto dto) {
        // Validate ticketType
        if (dto.ticketType == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }
        
        Ticket ticket = new Ticket();
        ticket.userId = dto.userId;
        ticket.originalRequest = dto.originalRequest;
        ticket.ticketType = dto.ticketType;
        ticket.status = dto.status != null ? dto.status : TicketStatus.FROM_DISPATCH;
        ticket.source = TicketSource.MANUAL;
        // TicketType defines intent; AssignedTeam is a derived consequence.
        ticket.assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType).name();
        // Auto-assign to default user if not specified
        ticket.assignedTo = dto.assignedTo != null ? dto.assignedTo : teamUserMapper.getDefaultUserIdForTeam(ticket.assignedTeam);
        ticket.urgencyFlag = dto.urgencyFlag != null ? dto.urgencyFlag : false;
        ticket.urgencyScore = dto.urgencyScore;
        ticket.rollbackAllowed = false;
        ticketRepository.persist(ticket);

        eventService.logEvent(
            EventType.TICKET_CREATED,
            EventSeverity.INFO,
            "ticketing-api",
            "Ticket #" + ticket.id + " created via API endpoint",
            ticket.id,
            null,
            null
        );

        // Send to similarity service (async, non-blocking)
        sendTicketToSimilarityService(ticket);

        return ticketMapper.toDto(ticket);
    }

    @Inject
    IncomingRequestService incomingRequestService;

    @Transactional
    public TicketDto createTicketFromAi(CreateTicketFromAiDto dto) {
        // Validate ticketType - AI can only suggest ticketType
        if (dto.ticketType == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null. AI must provide a valid ticketType.");
        }
        
        Ticket ticket = new Ticket();
        ticket.userId = dto.userId;
        ticket.originalRequest = dto.originalRequest;
        ticket.ticketType = dto.ticketType;
        ticket.status = TicketStatus.FROM_AI;
        ticket.source = TicketSource.AI;
        // TicketType defines intent; AssignedTeam is a derived consequence.
        ticket.assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType).name();
        // Auto-assign to default user for the team
        ticket.assignedTo = teamUserMapper.getDefaultUserIdForTeam(ticket.assignedTeam);
        ticket.urgencyFlag = dto.urgencyFlag != null ? dto.urgencyFlag : false;
        ticket.urgencyScore = dto.urgencyScore;
        ticket.aiConfidence = dto.aiConfidence;
        ticket.rollbackAllowed = true;
        ticket.aiPayloadJson = dto.aiPayloadJson;
        ticket.incomingRequestId = dto.incomingRequestId;
        ticketRepository.persist(ticket);

        // If this ticket was created from an incoming request, mark it as converted
        if (dto.incomingRequestId != null) {
            incomingRequestService.markAsConvertedToTicket(dto.incomingRequestId);
        }

        eventService.logEvent(
            EventType.AI_TICKET_CREATED,
            EventSeverity.INFO,
            "ai-triage-worker",
            "AI ticket #" + ticket.id + " created from incoming request #" + (dto.incomingRequestId != null ? dto.incomingRequestId : "N/A"),
            ticket.id,
            dto.incomingRequestId,
            dto.aiPayloadJson
        );

        // Send to similarity service (async, non-blocking)
        sendTicketToSimilarityService(ticket);

        return ticketMapper.toDto(ticket);
    }

    public List<TicketDto> getTickets(String view, String team, String user) {
        List<Ticket> tickets;
        if ("team".equals(view) && team != null) {
            tickets = ticketRepository.findByAssignedTeam(team);
        } else if ("mine".equals(view) && user != null) {
            tickets = ticketRepository.findByAssignedTo(user);
        } else {
            tickets = ticketRepository.listAll();
        }
        
        // Filter out ROLLED_BACK tickets from normal views
        tickets = tickets.stream()
            .filter(t -> t.status != TicketStatus.ROLLED_BACK)
            .collect(Collectors.toList());
        return tickets.stream()
            .map(ticket -> {
                TicketDto dto = ticketMapper.toDto(ticket);
                dto.aiPayloadJson = filterAiPayloadForCurrentActor(dto.aiPayloadJson);
                return dto;
            })
            .collect(Collectors.toList());
    }

    public TicketDto getTicket(Long id) {
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }
        TicketDto dto = ticketMapper.toDto(ticket);
        dto.aiPayloadJson = filterAiPayloadForCurrentActor(dto.aiPayloadJson);
        List<TicketComment> comments = commentRepository.findByTicketId(id);
        dto.comments = comments.stream()
            .map(c -> new CommentDto(c.id, c.getTicketId(), c.getAuthorId(), c.getBody()))
            .collect(Collectors.toList());
        return dto;
    }

    @Transactional
    public TicketDto acceptTicket(Long id, String userId) {
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.status = TicketStatus.TRIAGED;
        ticket.assignedTo = userId;
        ticketRepository.persist(ticket);

        eventService.logEvent(
            EventType.TICKET_ACCEPTED,
            EventSeverity.INFO,
            "ticketing-ui",
            "Ticket #" + ticket.id + " accepted by " + userId,
            ticket.id,
            null,
            null
        );

        return ticketMapper.toDto(ticket);
    }

    @Transactional
    public TicketDto rejectAndReturnToDispatch(Long id) {
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.status = TicketStatus.RETURNED_TO_DISPATCH;
        ticket.assignedTeam = "DISPATCH";
        ticket.assignedTo = null;
        ticketRepository.persist(ticket);

        eventService.logEvent(
            EventType.RETURNED_TO_DISPATCH,
            EventSeverity.INFO,
            "ticketing-ui",
            "Ticket #" + ticket.id + " returned to dispatch",
            ticket.id,
            null,
            null
        );

        return ticketMapper.toDto(ticket);
    }

    @Transactional
    public TicketDto updateTicketStatus(Long id, UpdateTicketStatusDto dto) {
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            return null;
        }
        ticket.status = dto.status;
        ticketRepository.persist(ticket);

        eventService.logEvent(
            EventType.TICKET_STATUS_CHANGED,
            EventSeverity.INFO,
            "ticketing-ui",
            "Ticket #" + ticket.id + " status changed to " + dto.status,
            ticket.id,
            null,
            null
        );

        return ticketMapper.toDto(ticket);
    }

    /**
     * Updates ticketType and automatically recomputes assignedTeam.
     * Dispatcher may change ticketType (not team); team updates automatically.
     * 
     * @param id Ticket ID
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
        if (dto.ticketType == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }
        
        TicketType oldType = ticket.ticketType;
        ticket.ticketType = dto.ticketType;
        // TicketType defines intent; AssignedTeam is a derived consequence.
        // Changing ticketType always recomputes assignedTeam.
        ticket.assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType).name();
        // Re-assign to default user for the new team if not already assigned
        if (ticket.assignedTo == null) {
            ticket.assignedTo = teamUserMapper.getDefaultUserIdForTeam(ticket.assignedTeam);
        }
        ticketRepository.persist(ticket);

        eventService.logEvent(
            EventType.TICKET_TYPE_CHANGED,
            EventSeverity.INFO,
            "ticketing-ui",
            "Ticket #" + ticket.id + " type changed from " + oldType + " to " + dto.ticketType + " (team: " + ticket.assignedTeam + ")",
            ticket.id,
            null,
            null
        );

        return ticketMapper.toDto(ticket);
    }

    @Transactional
    public CommentDto addComment(Long ticketId, AddCommentDto dto) {
        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            return null;
        }
        TicketComment comment = new TicketComment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(dto.authorId);
        comment.setBody(dto.body);
        commentRepository.persist(comment);

        eventService.logEvent(
            EventType.TICKET_COMMENT_ADDED,
            EventSeverity.INFO,
            "ticketing-ui",
            "Comment added to ticket #" + ticketId + " by " + dto.authorId,
            ticketId,
            null,
            null
        );

        return new CommentDto(comment.id, comment.getTicketId(), comment.getAuthorId(), comment.getBody());
    }

    @Transactional
    public TicketDto rollbackToRequest(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            return null;
        }

        // Verify this is an AI ticket that can be rolled back
        if (ticket.status != TicketStatus.FROM_AI || !ticket.rollbackAllowed) {
            throw new IllegalArgumentException("Ticket cannot be rolled back. Only FROM_AI tickets with rollbackAllowed=true can be rolled back.");
        }

        if (ticket.incomingRequestId == null) {
            throw new IllegalArgumentException("Ticket has no associated incoming request to roll back to.");
        }

        // Mark ticket as rolled back
        ticket.status = TicketStatus.ROLLED_BACK;
        ticketRepository.persist(ticket);

        // Mark incoming request as returned from AI
        incomingRequestService.markAsReturnedFromAi(ticket.incomingRequestId);

        // Emit event
        eventService.logEvent(
            EventType.AI_TICKET_ROLLED_BACK,
            EventSeverity.INFO,
            "ticketing-ui",
            "AI ticket #" + ticket.id + " rolled back to incoming request #" + ticket.incomingRequestId,
            ticket.id,
            ticket.incomingRequestId,
            null
        );

        return ticketMapper.toDto(ticket);
    }

    /**
     * Create a placeholder ticket for triage processing.
     * This ticket will be updated with triage results later.
     */
    @Transactional
    public Ticket createPlaceholderTicketForTriage(String userId, String originalRequest, Long incomingRequestId) {
        Ticket ticket = new Ticket();
        ticket.userId = userId;
        ticket.originalRequest = originalRequest;
        ticket.ticketType = TicketType.OTHER; // Placeholder - will be updated by triage
        ticket.status = TicketStatus.FROM_AI; // Will be updated after triage
        ticket.source = TicketSource.AI;
        ticket.assignedTeam = "DISPATCH"; // Default team until triage completes
        ticket.assignedTo = null;
        ticket.urgencyFlag = false;
        ticket.urgencyScore = null;
        ticket.aiConfidence = null;
        ticket.rollbackAllowed = true;
        ticket.aiPayloadJson = "{}";
        ticket.incomingRequestId = incomingRequestId;
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
        
        ticket.ticketType = TicketType.valueOf(ticketType);
        ticket.urgencyScore = urgencyScore != null ? urgencyScore : 5.0;
        ticket.urgencyFlag = ticket.urgencyScore >= 7.0; // Flag as urgent if score >= 7
        ticket.aiConfidence = aiConfidence != null ? aiConfidence / 100.0 : 0.0;
        
        // Update assigned team based on new ticket type
        ticket.assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(ticket.ticketType).name();
        ticket.assignedTo = teamUserMapper.getDefaultUserIdForTeam(ticket.assignedTeam);
        
        // Store relatedTicketIds and policyCitations in aiPayloadJson
        Map<String, Object> aiPayload = new HashMap<>();
        aiPayload.put("relatedTicketIds", relatedTicketIds != null ? relatedTicketIds : List.of());
        aiPayload.put("policyCitations", policyCitations != null ? policyCitations : List.of());
        try {
            ticket.aiPayloadJson = objectMapper.writeValueAsString(aiPayload);
        } catch (Exception e) {
            System.err.println("Error serializing AI payload: " + e.getMessage());
            ticket.aiPayloadJson = "{}";
        }
        
        ticketRepository.persist(ticket);
        
        // Mark incoming request as converted if it exists
        if (ticket.incomingRequestId != null) {
            incomingRequestService.markAsConvertedToTicket(ticket.incomingRequestId);
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
        SimilarityUpsertRequestDto request = new SimilarityUpsertRequestDto();
        request.ticketId = ticket.id;
        request.ticketType = ticket.ticketType.name();
        request.text = ticket.originalRequest;

        similarityServiceClient.upsertTicketAsync(request)
            .thenAccept(response -> {
                // Success - log event
                eventService.logEvent(
                    EventType.SYSTEM_STEP,
                    EventSeverity.INFO,
                    "ticketing-api",
                    "Sending ticket #" + ticket.id + " to embedding service",
                    ticket.id,
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
                    ticket.id,
                    null,
                    null
                );
                return null;
            });
    }
}
