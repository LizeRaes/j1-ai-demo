package com.example.ticket.dto;

import com.example.ticket.domain.constants.TicketStatus;
import com.example.ticket.domain.constants.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTicketManualDto(String userId, String originalRequest, TicketType ticketType, TicketStatus status,
                                    String assignedTo, Boolean urgencyFlag, Double urgencyScore) {
    // assignedTeam is derived from ticketType automatically - not part of DTO,
}
