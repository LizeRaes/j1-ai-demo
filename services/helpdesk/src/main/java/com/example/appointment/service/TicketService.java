package com.example.appointment.service;

import com.example.appointment.domain.constants.*;
import com.example.appointment.domain.model.Ticket;
import com.example.appointment.domain.model.Comment;
import com.example.appointment.domain.model.TicketPullRequest;
import com.example.appointment.dto.*;
import com.example.appointment.external.CodingAssistantClient;
import com.example.appointment.external.TriageClient;
import com.example.appointment.mapper.TicketMapper;
import com.example.appointment.mapper.TicketTypeTeamMapper;
import com.example.appointment.service.adapter.CommentService;
import com.example.appointment.service.adapter.EventService;
import com.example.appointment.service.adapter.IncomingRequestService;
import com.example.appointment.service.adapter.TicketStateService;
import com.example.appointment.service.adapter.TicketPullRequestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    CodingAssistantClient codingAssistantClient;

    @Inject
    @RestClient
    TriageClient triageClient;

    @Inject
    IncomingRequestService incomingRequestService;

    @Inject
    TicketPullRequestService ticketPullRequestService;

    @ConfigProperty(name = "app.coding-assistant.enabled", defaultValue = "true")
    boolean codingAssistantEnabled;

    @ConfigProperty(name = "app.coding-assistant.repo-url")
    Optional<String> codingAssistantRepoUrl;

    @ConfigProperty(name = "app.coding-assistant.confidence-threshold")
    Double codingAssistantConfidenceThreshold;

    @Transactional
    public TicketDto submit(DispatchedTicketDto dto) {
        var incomingRequest = incomingRequestService.getIncomingRequest(dto.incomingRequestId());
        if (incomingRequest == null) {
            throw new IllegalArgumentException("Incoming request not found: " + dto.incomingRequestId());
        }

        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        TicketMapper ticketMapper = new TicketMapper();
        Ticket ticket = ticketMapper.toDispatchTicket(dto, incomingRequest);
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

        TicketDto ticketDto = ticketMapper.toTicketDto(ticket);

        incomingRequestService.markAsConvertedToTicket(dto.incomingRequestId());
        notifyTicketSyncUpsertBestEffort(ticket, "dispatch-submit");

        return ticketDto;
    }

    @Transactional
    public TicketDto manualSubmit(ManualTicketDto dto) {
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        TicketMapper ticketMapper =  new TicketMapper();
        Ticket ticket = ticketMapper.toManualTicket(dto);
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

        notifyTicketSyncUpsertBestEffort(ticket, "manual-submit");
        return ticketMapper.toTicketDto(ticket);
    }

    @Transactional
    public TicketDto aiSubmit(AITicketDto dto) {
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null. AI must provide a valid ticketType.");
        }

        TicketMapper ticketMapper = new TicketMapper();
        Ticket ticket = ticketMapper.toAiTicket(dto);
        ticketStateService.persist(ticket);

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

        notifyTicketSyncUpsertBestEffort(ticket, "ai-submit");
        return ticketMapper.toTicketDto(ticket);
    }

    public List<TicketDto> findTickets(String view, String team, String user) {
        String normalizedView = view == null ? "all" : view;
        String normalizedTeam = team == null ? null : team.trim().toLowerCase();
        List<Ticket> tickets = switch (normalizedView) {
            case "team" -> ticketStateService.findByAssignedTeam(normalizedTeam);
            case String v when (user != null && v.equals("mine")) -> ticketStateService.findByAssignedTo(user);
            case String _ -> ticketStateService.listAll();
        };

        tickets = tickets.stream()
                .filter(this::isVisibleToUi)
                .toList();
        return tickets.stream()
                .map(ticket -> new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                        ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                        ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                        ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                        filterAiPayloadForCurrentActor(ticket.getAiPayloadJson()), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                        ticket.getUpdatedAt(), List.of(), mapPullRequests(ticket.getId())))
                .toList();
    }

    public TicketDto findTicket(Long id) {
        Ticket ticket = ticketStateService.findById(id);
        if (ticket == null || !isVisibleToUi(ticket)) {
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
                .toList(), mapPullRequests(ticket.getId()));
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

    @Transactional
    public TicketDto updateTicketType(Long id, UpdateTicketTypeDto dto) {
        if (dto.ticketType() == null) {
            throw new IllegalArgumentException("TicketType is required and cannot be null");
        }

        Ticket ticket = ticketStateService.findById(id);
        if (ticket == null) {
            return null;
        }

        TicketType oldType = ticket.getTicketType();
        ticket.setTicketType(dto.ticketType());
        TicketMapper ticketMapper = new TicketMapper();
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        ticket.setAssignedTeam(ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType()).name());
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

        if (ticket.getStatus() != TicketStatus.FROM_AI || !ticket.getRollbackAllowed()) {
            throw new IllegalArgumentException("Ticket cannot be rolled back. Only FROM_AI tickets with rollbackAllowed=true can be rolled back.");
        }

        if (ticket.getIncomingRequestId() == null) {
            throw new IllegalArgumentException("Ticket has no associated incoming request to roll back to.");
        }

        ticket.setStatus(TicketStatus.RETURNED_TO_DISPATCH);
        ticketStateService.persist(ticket);

        incomingRequestService.markAsReturnedFromAi(ticket.getIncomingRequestId());

        eventService.logEvent(
                EventType.AI_TICKET_ROLLED_BACK,
                EventSeverity.INFO,
                "ticketing-ui",
                "AI ticket #" + ticket.getId() + " returned to dispatch queue (incoming request #" + ticket.getIncomingRequestId() + ").",
                ticket.getId(),
                ticket.getIncomingRequestId(),
                null
        );

        notifyTicketSyncDeleteBestEffort(ticket.getId(), ticket.getIncomingRequestId(), "rollback-to-dispatch");

        return new TicketMapper().toTicketDto(ticket);
    }

    @Transactional
    public Ticket createInitialTicketForTriage(String userId, String originalRequest, Long incomingRequestId) {
        TicketMapper ticketMapper = new TicketMapper();
        Ticket ticket = ticketMapper.toInitialTriageTicket(userId, originalRequest, incomingRequestId);
        ticketStateService.persist(ticket);
        return ticket;
    }

    @Transactional
    public TicketPullRequestDto addManualPullRequest(Long ticketId, AddPullRequestDto dto) {
        if (dto == null || dto.prUrl() == null || dto.prUrl().isBlank()) {
            throw new IllegalArgumentException("prUrl is required.");
        }

        Ticket ticket = ticketStateService.findById(ticketId);
        if (ticket == null) {
            return null;
        }

        TicketPullRequest pullRequest = new TicketPullRequest();
        pullRequest.setTicket(ticket);
        pullRequest.setPrUrl(dto.prUrl().trim());
        pullRequest.setAiGenerated(false);
        ticketPullRequestService.persist(pullRequest);

        eventService.logEvent(
                EventType.SYSTEM_STEP,
                EventSeverity.INFO,
                "ticketing-ui",
                "Manual PR added to ticket #" + ticketId,
                ticketId,
                null,
                null
        );

        return toPullRequestDto(pullRequest);
    }

    @Transactional
    public void upsertAiPullRequest(Long ticketId, String prUrl) {
        if (ticketId == null || prUrl == null || prUrl.isBlank()) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "coding-assistant-callback",
                    "Ignoring coding assistant callback due to missing ticketId/prUrl.",
                    ticketId,
                    null,
                    null
            );
            return;
        }

        Ticket ticket = ticketStateService.findById(ticketId);
        if (ticket == null) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "coding-assistant-callback",
                    "Ignoring coding assistant callback for unknown ticket #" + ticketId,
                    ticketId,
                    null,
                    null
            );
            return;
        }

        TicketPullRequest existingAiPr = ticketPullRequestService.findAiPullRequestByTicketId(ticketId);
        String normalizedPrUrl = prUrl.trim();
        if (existingAiPr != null) {
            existingAiPr.setPrUrl(normalizedPrUrl);
            ticketPullRequestService.persist(existingAiPr);
            eventService.logEvent(
                    EventType.SYSTEM_STEP,
                    EventSeverity.INFO,
                    "coding-assistant-callback",
                    "AI PR updated for ticket #" + ticketId + " (existing AI PR overwritten).",
                    ticketId,
                    null,
                    null
            );
            return;
        }

        TicketPullRequest created = new TicketPullRequest();
        created.setTicket(ticket);
        created.setPrUrl(normalizedPrUrl);
        created.setAiGenerated(true);
        ticketPullRequestService.persist(created);
        eventService.logEvent(
                EventType.SYSTEM_STEP,
                EventSeverity.INFO,
                "coding-assistant-callback",
                "AI PR added to ticket #" + ticketId,
                ticketId,
                null,
                null
        );
    }

    private List<TicketPullRequestDto> mapPullRequests(Long ticketId) {
        return ticketPullRequestService.findByTicketId(ticketId).stream()
                .map(this::toPullRequestDto)
                .toList();
    }

    private TicketPullRequestDto toPullRequestDto(TicketPullRequest pr) {
        return new TicketPullRequestDto(
                pr.getId(),
                pr.getPrUrl(),
                pr.getAiGenerated()
        );
    }

    private boolean isBugTicketType(TicketType ticketType) {
        return ticketType == TicketType.BUG_APP || ticketType == TicketType.BUG_BACKEND;
    }

    private boolean isVisibleToUi(Ticket ticket) {
        if (ticket == null) {
            return false;
        }
        return ticket.getStatus() != TicketStatus.AI_TRIAGE_PENDING
                && ticket.getStatus() != TicketStatus.RETURNED_TO_DISPATCH;
    }

    private void triggerCodingAssistantBestEffort(Ticket ticket) {
        if (!codingAssistantEnabled) {
            eventService.logEvent(
                    EventType.SYSTEM_STEP,
                    EventSeverity.INFO,
                    "ticketing-api",
                    "Coding assistant trigger skipped for ticket #" + ticket.getId() + " (disabled).",
                    ticket.getId(),
                    ticket.getIncomingRequestId(),
                    null
            );
            return;
        }

        String repoUrl = codingAssistantRepoUrl.map(String::trim).orElse("");
        if (repoUrl.isBlank()) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "ticketing-api",
                    "Coding assistant trigger skipped for ticket #" + ticket.getId() + " (repo URL not configured).",
                    ticket.getId(),
                    ticket.getIncomingRequestId(),
                    null
            );
            return;
        }

        CodingAssistantSubmitJobRequestDto request = new CodingAssistantSubmitJobRequestDto(
                ticket.getId(),
                ticket.getOriginalRequest(),
                repoUrl,
                codingAssistantConfidenceThreshold
        );

        try {
            eventService.logEvent(
                    EventType.SYSTEM_STEP,
                    EventSeverity.INFO,
                    "ticketing-api",
                    "NEW JOB CAME IN: " + ticket.getOriginalRequest(),
                    ticket.getId(),
                    ticket.getIncomingRequestId(),
                    null
            );
            CodingAssistantSubmitJobResponseDto response = codingAssistantClient.submitJob(request);
            String status = response != null && response.status() != null ? response.status() : "UNKNOWN";
            String message = response != null && response.message() != null ? response.message() : "";
            eventService.logEvent(
                    EventType.SYSTEM_STEP,
                    EventSeverity.INFO,
                    "ticketing-api",
                    "Coding assistant trigger for ticket #" + ticket.getId() + " returned status " + status + ". " + message,
                    ticket.getId(),
                    ticket.getIncomingRequestId(),
                    null
            );
        } catch (Exception e) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "ticketing-api",
                    "Coding assistant trigger could not be started for ticket #" + ticket.getId() + ": " + e.getMessage(),
                    ticket.getId(),
                    ticket.getIncomingRequestId(),
                    null
            );
        }
    }

    private void notifyTicketSyncUpsertBestEffort(Ticket ticket, String source) {
        if (ticket == null || ticket.getId() == null || ticket.getTicketType() == null || ticket.getOriginalRequest() == null) {
            return;
        }
        try {
            triageClient.notifyTicketUpsert(new TicketSyncUpsertDto(
                    ticket.getId(),
                    ticket.getTicketType().name(),
                    ticket.getOriginalRequest()
            ));
            eventService.logEvent(
                    EventType.SYSTEM_STEP,
                    EventSeverity.INFO,
                    "ticketing-api",
                    "Ticket sync upsert notification sent for ticket #" + ticket.getId() + " (" + source + ").",
                    ticket.getId(),
                    ticket.getIncomingRequestId(),
                    null
            );
        } catch (Exception e) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "ticketing-api",
                    "Ticket sync upsert notification failed for ticket #" + ticket.getId() + " (" + source + "): " + e.getMessage(),
                    ticket.getId(),
                    ticket.getIncomingRequestId(),
                    null
            );
        }
    }

    private void notifyTicketSyncDeleteBestEffort(Long ticketId, Long incomingRequestId, String source) {
        if (ticketId == null) {
            return;
        }
        try {
            triageClient.notifyTicketDelete(ticketId);
            eventService.logEvent(
                    EventType.SYSTEM_STEP,
                    EventSeverity.INFO,
                    "ticketing-api",
                    "Ticket sync delete notification sent for ticket #" + ticketId + " (" + source + ").",
                    ticketId,
                    incomingRequestId,
                    null
            );
        } catch (Exception e) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "ticketing-api",
                    "Ticket sync delete notification failed for ticket #" + ticketId + " (" + source + "): " + e.getMessage(),
                    ticketId,
                    incomingRequestId,
                    null
            );
        }
    }

    private String filterAiPayloadForCurrentActor(String aiPayloadJson) {
        if (aiPayloadJson == null || aiPayloadJson.isBlank()) {
            return aiPayloadJson;
        }
        String actorTeam;
        try {
            if (requestContext == null) return aiPayloadJson;
            String team = requestContext.getHeaderString("X-Actor-Team");
            actorTeam = (team != null) ? team : Team.DISPATCHING.value();
        } catch (Exception e) {
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

            String normalizedTeam = actorTeam.trim().toLowerCase();
            ArrayNode filtered = objectMapper.createArrayNode();
            for (JsonNode citation : citationsNode) {
                if (citation == null || !citation.isObject()) continue;
                JsonNode rbacTeamsNode = citation.get("rbacTeams");
                if (rbacTeamsNode == null || !rbacTeamsNode.isArray()) continue;

                // Empty RBAC list means company-wide visibility.
                if (rbacTeamsNode.isEmpty()) {
                    filtered.add(citation);
                    continue;
                }

                boolean hasAccess = false;
                for (JsonNode teamNode : rbacTeamsNode) {
                    if (teamNode != null && teamNode.isTextual()) {
                        String normalizedCitationTeam = teamNode.asText().trim().toLowerCase();
                        if (!normalizedCitationTeam.isBlank() && normalizedCitationTeam.equals(normalizedTeam)) {
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

    @Transactional
    public void updateTicketWithTriageResults(Long ticketId, String ticketType, Double urgencyScore, Double aiConfidence,
                                              List<Long> relatedTicketIds, List<TriageResponseDto.PolicyCitation> policyCitations) {
        Ticket ticket = ticketStateService.findById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found: " + ticketId);
        }

        ticket.setTicketType(TicketType.valueOf(ticketType));
        ticket.setStatus(TicketStatus.FROM_AI);
        ticket.setUrgencyScore(urgencyScore != null ? urgencyScore : 5.0);
        ticket.setUrgencyFlag(ticket.getUrgencyScore() >= 7.0);
        ticket.setAiConfidence(aiConfidence != null ? aiConfidence / 100.0 : 0.0);

        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        ticket.setAssignedTeam(ticketTypeTeamMapper.deriveTeamFromTicketType(ticket.getTicketType()).value());
        TicketMapper ticketMapper = new TicketMapper();
        String assignedTo = ticketMapper.mapUserIdForTeam(ticket.getAssignedTeam());
        ticket.setAssignedTo(assignedTo);

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

        if (ticket.getIncomingRequestId() != null) {
            incomingRequestService.markAsConvertedToTicket(ticket.getIncomingRequestId());
        }

        if (isBugTicketType(ticket.getTicketType())) {
            triggerCodingAssistantBestEffort(ticket);
        }

        // Only after successful triage result is accepted into helpdesk state.
        notifyTicketSyncUpsertBestEffort(ticket, "ai-triage-success");

    }

    @Transactional
    public void updateTicketAsFallback(Long ticketId, IncomingRequestDto request, String failReason) {
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

        Ticket ticket = ticketStateService.findById(ticketId);
        if (ticket != null) {
            ticket.setStatus(TicketStatus.RETURNED_TO_DISPATCH);
            ticket.setTicketType(TicketType.OTHER);
            ticket.setAssignedTeam(Team.DISPATCHING.value());
            ticket.setAssignedTo(null);
            ticket.setUrgencyFlag(false);
            ticket.setUrgencyScore(5.0);
            ticket.setAiConfidence(0.0);
            ticket.setRollbackAllowed(false);
            ticket.setAiPayloadJson(aiPayloadJson);
            ticketStateService.persist(ticket);
        }

        if (request != null && request.id() != null) {
            incomingRequestService.markAsAiTriageFailed(request.id());
        }
        Long requestId = request != null ? request.id() : null;

        eventService.logEvent(
                EventType.AI_TRIAGE_FAILED,
                EventSeverity.WARNING,
                "ai-triage-worker",
                "AI triage failed for request #" + requestId + " (ticket #" + ticketId + " returned to dispatch): " + failReason,
                ticketId,
                requestId,
                aiPayloadJson
        );
    }

}
