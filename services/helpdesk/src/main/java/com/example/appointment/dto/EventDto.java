package com.example.ticket.dto;

import com.example.ticket.domain.constants.EventSeverity;
import com.example.ticket.domain.constants.EventType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDto(Long id, Long ticketId, Long incomingRequestId,
                       EventType eventType, EventSeverity severity,
                       String source, String message,
                       String payloadJson, java.time.LocalDateTime createdAt) {
}
