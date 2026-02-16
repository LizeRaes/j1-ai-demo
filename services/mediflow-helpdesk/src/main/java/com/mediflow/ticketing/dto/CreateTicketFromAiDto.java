package com.mediflow.ticketing.dto;

import com.mediflow.ticketing.domain.enums.TicketType;

public class CreateTicketFromAiDto {
    public String userId;
    public String originalRequest;
    public TicketType ticketType;
    // assignedTeam is derived from ticketType automatically - not part of DTO
    // AI is only allowed to suggest ticketType
    public Boolean urgencyFlag;
    public Double urgencyScore;
    public Double aiConfidence;
    public String aiPayloadJson;
    public Long incomingRequestId;
}
