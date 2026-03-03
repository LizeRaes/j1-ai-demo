package com.example.ticket.dto;

import com.example.ticket.domain.constants.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AITicketDto(String userId, String originalRequest, TicketType ticketType,
                          double urgencyScore, boolean urgencyFlag, Double aiConfidence,
                          String aiPayloadJson, Long incomingRequestId) {
    // assignedTeam is derived from ticketType automatically - not part of DTO
    // AI is only allowed to suggest ticketType

}
