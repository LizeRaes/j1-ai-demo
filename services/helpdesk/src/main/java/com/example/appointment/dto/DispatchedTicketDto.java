package com.example.appointment.dto;

import com.example.appointment.domain.constants.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DispatchedTicketDto(Long incomingRequestId, TicketType ticketType,
                                  Boolean urgencyFlag, Double urgencyScore, String dispatcherId, String notes) {
    // assignedTeam is derived from ticketType automatically - not part of DTO
}
