package com.mediflow.ticketing.mapper;

import com.mediflow.ticketing.domain.model.Ticket;
import com.mediflow.ticketing.dto.TicketDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TicketMapper {
    public TicketDto toDto(Ticket ticket) {
        TicketDto dto = new TicketDto();
        dto.id = ticket.id;
        dto.userId = ticket.userId;
        dto.originalRequest = ticket.originalRequest;
        dto.ticketType = ticket.ticketType;
        dto.status = ticket.status;
        dto.source = ticket.source;
        dto.assignedTeam = ticket.assignedTeam;
        dto.assignedTo = ticket.assignedTo;
        dto.urgencyFlag = ticket.urgencyFlag;
        dto.urgencyScore = ticket.urgencyScore;
        dto.aiConfidence = ticket.aiConfidence;
        dto.rollbackAllowed = ticket.rollbackAllowed;
        dto.aiPayloadJson = ticket.aiPayloadJson;
        dto.incomingRequestId = ticket.incomingRequestId;
        dto.createdAt = ticket.createdAt;
        dto.updatedAt = ticket.updatedAt;
        return dto;
    }
}
