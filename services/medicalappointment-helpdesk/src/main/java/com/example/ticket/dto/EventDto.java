package com.example.ticket.dto;

import com.example.ticket.domain.enums.EventSeverity;
import com.example.ticket.domain.enums.EventType;
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
