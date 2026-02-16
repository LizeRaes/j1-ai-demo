package com.mediflow.ticketing.dto;

import com.mediflow.ticketing.domain.enums.TicketStatus;
import com.mediflow.ticketing.domain.enums.TicketType;

public class CreateTicketManualDto {
    public String userId;
    public String originalRequest;
    public TicketType ticketType;
    public TicketStatus status;
    // assignedTeam is derived from ticketType automatically - not part of DTO
    public String assignedTo;
    public Boolean urgencyFlag;
    public Double urgencyScore;
}
