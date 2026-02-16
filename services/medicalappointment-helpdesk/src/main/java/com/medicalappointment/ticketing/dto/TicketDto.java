package com.medicalappointment.ticketing.dto;

import com.medicalappointment.ticketing.domain.enums.TicketSource;
import com.medicalappointment.ticketing.domain.enums.TicketStatus;
import com.medicalappointment.ticketing.domain.enums.TicketType;
import java.time.OffsetDateTime;
import java.util.List;

public class TicketDto {
    public Long id;
    public String userId;
    public String originalRequest;
    public TicketType ticketType;
    public TicketStatus status;
    public TicketSource source;
    public String assignedTeam;
    public String assignedTo;
    public Boolean urgencyFlag;
    public Double urgencyScore;
    public Double aiConfidence;
    public Boolean rollbackAllowed;
    public String aiPayloadJson;
    public Long incomingRequestId;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public List<CommentDto> comments;
}
