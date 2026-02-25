package com.example.ticket.dto;

import com.example.ticket.domain.constants.TicketType;

public class DispatchCreateTicketDto {
    public Long incomingRequestId;
    public TicketType ticketType;
    // assignedTeam is derived from ticketType automatically - not part of DTO
    public Boolean urgencyFlag;
    public Double urgencyScore;
    public String dispatcherId;
    public String notes;
}
