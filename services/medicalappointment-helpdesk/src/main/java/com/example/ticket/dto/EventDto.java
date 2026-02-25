package com.example.ticket.dto;

import com.example.ticket.domain.constants.EventSeverity;
import com.example.ticket.domain.constants.EventType;

public record EventDto (Long id, Long ticketId, Long incomingRequestId,
                        EventType eventType, EventSeverity severity,
                        String source, String message,
                        String payloadJson, java.time.LocalDateTime createdAt) {
}
