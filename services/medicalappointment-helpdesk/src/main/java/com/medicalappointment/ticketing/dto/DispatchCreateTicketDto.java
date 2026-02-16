package com.medicalappointment.ticketing.dto;

import com.medicalappointment.ticketing.domain.enums.TicketType;

public class DispatchCreateTicketDto {
    public Long incomingRequestId;
    public TicketType ticketType;
    // assignedTeam is derived from ticketType automatically - not part of DTO
    public Boolean urgencyFlag;
    public Double urgencyScore;
    public String dispatcherId;
    public String notes;
}
