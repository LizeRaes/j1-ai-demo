package com.example.ticket.dto;

import com.example.ticket.domain.constants.TicketSource;
import com.example.ticket.domain.constants.TicketStatus;
import com.example.ticket.domain.constants.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketDto(Long id, String userId, String originalRequest,
                        TicketType ticketType, TicketStatus status, TicketSource source,
                        String assignedTeam, String assignedTo, Boolean urgencyFlag, Double urgencyScore,
                        Double aiConfidence, Boolean rollbackAllowed, String aiPayloadJson, Long incomingRequestId,
                        LocalDateTime createdAt, LocalDateTime updatedAt, List<CommentDto> comments) {
}
