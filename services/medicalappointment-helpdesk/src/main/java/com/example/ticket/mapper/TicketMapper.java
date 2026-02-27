package com.example.ticket.mapper;

import com.example.ticket.domain.constants.Team;
import com.example.ticket.domain.constants.TicketSource;
import com.example.ticket.domain.constants.TicketStatus;
import com.example.ticket.domain.constants.TicketType;
import com.example.ticket.domain.model.Ticket;
import com.example.ticket.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.NonNull;

import java.util.List;

@ApplicationScoped
public class TicketMapper {

    public Ticket toDispatchTicket(DispatchedTicketDto dto, IncomingRequestDto requestDto, Long nextId) {
        Ticket ticket = new Ticket();
        ticket.setId(nextId);
        ticket.setUserId(requestDto.userId());
        ticket.setOriginalRequest(requestDto.rawText());
        ticket.setTicketType(dto.ticketType());
        ticket.setStatus(TicketStatus.FROM_DISPATCH);
        ticket.setSource(TicketSource.MANUAL);
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        String assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType()).name();
        String assignedTo = mapUserIdForTeam(assignedTeam);
        ticket.setAssignedTeam(assignedTeam);
        ticket.setAssignedTo(assignedTo);
        ticket.setUrgencyFlag(dto.urgencyFlag() != null ? dto.urgencyFlag() : false);
        ticket.setUrgencyScore(dto.urgencyScore());
        ticket.setRollbackAllowed(false);
        ticket.setIncomingRequestId(dto.incomingRequestId());
        return ticket;
    }

    public Ticket toManualTicket(ManualTicketDto dto, Long nextId) {
        Ticket ticket = new Ticket();
        ticket.setId(nextId);
        ticket.setUserId(dto.userId());
        ticket.setOriginalRequest(dto.originalRequest());
        ticket.setTicketType(dto.ticketType());
        ticket.setStatus(dto.status() != null ? dto.status() : TicketStatus.FROM_DISPATCH);
        ticket.setSource(TicketSource.MANUAL);
        // TicketType defines intent; AssignedTeam is a derived consequence.
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        Team assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType());
        String assignedTo = dto.assignedTo() != null ? dto.assignedTo() : mapUserIdForTeam(assignedTeam.name());
        ticket.setAssignedTeam(assignedTeam.name());
        ticket.setAssignedTo(assignedTo);
        ticket.setUrgencyFlag(dto.urgencyFlag() != null ? dto.urgencyFlag() : false);
        ticket.setUrgencyScore(dto.urgencyScore());
        ticket.setRollbackAllowed(false);
        return ticket;
    }

    public Ticket toAiTicket(AITicketDto dto, Long nextId) {
        Ticket ticket = new Ticket();
        ticket.setId(nextId);
        ticket.setUserId(dto.userId());
        ticket.setOriginalRequest(dto.originalRequest());
        ticket.setTicketType(dto.ticketType());
        ticket.setStatus(TicketStatus.FROM_AI);
        ticket.setSource(TicketSource.AI);
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        String assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(dto.ticketType()).name();
        String assignedTo = mapUserIdForTeam(assignedTeam);
        ticket.setAssignedTeam(assignedTeam);
        ticket.setAssignedTo(assignedTo);
        ticket.setUrgencyFlag(dto.urgencyFlag());
        ticket.setUrgencyScore(dto.urgencyScore());
        ticket.setAiConfidence(dto.aiConfidence());
        ticket.setRollbackAllowed(true);
        ticket.setAiPayloadJson(dto.aiPayloadJson());
        ticket.setIncomingRequestId(dto.incomingRequestId());
        return ticket;
    }

    public Ticket toPlaceholderTicket(String userId, String originalRequest, Long incomingRequestId) {
        Ticket ticket = new Ticket();
        ticket.setUserId(userId);
        ticket.setOriginalRequest(originalRequest);
        ticket.setTicketType(TicketType.OTHER);
        ticket.setStatus(TicketStatus.FROM_AI);
        ticket.setSource(TicketSource.AI);
        ticket.setAssignedTeam("DISPATCH");
        ticket.setUrgencyFlag(false);
        ticket.setRollbackAllowed(true);
        ticket.setAiPayloadJson("{}");
        ticket.setIncomingRequestId(incomingRequestId);
        return ticket;
    }

    public String mapUserIdForTeam(String team) {
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

    public TicketDto toTicketDto(Ticket ticket) {

        return new TicketDto(ticket.getId(), ticket.getUserId(), ticket.getOriginalRequest(),
                ticket.getTicketType(), ticket.getStatus(), ticket.getSource(),
                ticket.getAssignedTeam(), ticket.getAssignedTo(), ticket.getUrgencyFlag(),
                ticket.getUrgencyScore(), ticket.getAiConfidence(), ticket.getRollbackAllowed(),
                ticket.getAiPayloadJson(), ticket.getIncomingRequestId(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), List.of());
    }

    public Ticket toTicket(TicketDto dto) {
        Ticket ticket = new Ticket();
        ticket.setId(dto.id());
        ticket.setUserId(dto.userId());
        ticket.setOriginalRequest(dto.originalRequest());
        ticket.setTicketType(dto.ticketType());
        ticket.setAiConfidence(dto.aiConfidence());
        ticket.setAiPayloadJson(dto.aiPayloadJson());
        TicketTypeTeamMapper ticketTypeTeamMapper = new TicketTypeTeamMapper();
        Team assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(ticket.getTicketType());
        ticket.setAssignedTeam(assignedTeam.name());
        TicketStatus interpretStatus = switch (dto.status().name()) {
            case "OPEN", "NEW", "PENDING" -> TicketStatus.FROM_DISPATCH;
            case "RESOLVED", "CLOSED" -> TicketStatus.COMPLETED;
            case "TRIAGED", "IN_PROGRESS", "WAITING_ON_USER", "COMPLETED", "RETURNED_TO_DISPATCH",
                 "ROLLED_BACK" -> TicketStatus.valueOf(dto.status().name());
            case String _ -> TicketStatus.FROM_DISPATCH;
        };
        ticket.setStatus(interpretStatus);
        ticket.setAssignedTo(dto.assignedTo() != null ? dto.assignedTo() : mapUserId(ticket.getAssignedTeam()));
        ticket.setSource(dto.source());
        ticket.setUrgencyFlag(dto.urgencyFlag());
        ticket.setUrgencyScore(dto.urgencyScore());
        ticket.setRollbackAllowed(dto.source().equals(TicketSource.AI) && dto.status().equals(TicketStatus.FROM_AI));


        return ticket;
    }

    private static String mapUserId(String team) {
        if (team == null) return "demo-user";

        return switch (team.toLowerCase()) {
            case "dispatch" -> "dispatch-user1";
            case "billing" -> "billing-user1";
            case "reschedule" -> "reschedule-user1";
            case "engineering" -> "engineering-user1";
            case String t -> t + "-user1";
        };
    }
}