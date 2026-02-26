package com.example.ticket.dto;

import com.example.ticket.domain.constants.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DispatchCreateTicketDto(Long incomingRequestId, TicketType ticketType,
                                      Boolean urgencyFlag, Double urgencyScore, String dispatcherId, String notes) {
    // assignedTeam is derived from ticketType automatically - not part of DTO
}
