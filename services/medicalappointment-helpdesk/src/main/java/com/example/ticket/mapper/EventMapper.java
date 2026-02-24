package com.example.ticket.mapper;

import com.example.ticket.domain.model.EventLog;
import com.example.ticket.dto.EventDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EventMapper {
    public EventDto toDto(EventLog event) {
        EventDto dto = new EventDto();
        dto.id = event.id;
        dto.ticketId = event.ticketId;
        dto.incomingRequestId = event.incomingRequestId;
        dto.eventType = event.eventType;
        dto.severity = event.severity;
        dto.source = event.source;
        dto.message = event.message;
        dto.payloadJson = event.payloadJson;
        dto.createdAt = event.createdAt;
        return dto;
    }
}
