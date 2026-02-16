package com.medicalappointment.ticketing.dto;

import com.medicalappointment.ticketing.domain.enums.EventSeverity;
import com.medicalappointment.ticketing.domain.enums.EventType;
import java.time.OffsetDateTime;

public class EventDto {
    public Long id;
    public Long ticketId;
    public Long incomingRequestId;
    public EventType eventType;
    public EventSeverity severity;
    public String source;
    public String message;
    public String payloadJson;
    public OffsetDateTime createdAt;
}
