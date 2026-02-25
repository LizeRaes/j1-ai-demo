package com.example.ticket.service;

import com.example.ticket.domain.constants.EventSeverity;
import com.example.ticket.domain.constants.EventType;
import com.example.ticket.domain.model.EventLog;
import com.example.ticket.dto.EventDto;
import com.example.ticket.persistence.EventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class EventService {
    @Inject
    EventLogRepository eventLogRepository;

    @Transactional
    public void logEvent(EventType eventType, EventSeverity severity, String source, String message, Long ticketId, Long incomingRequestId, String payloadJson) {
        EventLog event = new EventLog();
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setSource(source);
        event.setMessage(message);
        event.setTicketId(ticketId);
        event.setIncomingRequestId(incomingRequestId);
        event.setPayloadJson(payloadJson);
        eventLogRepository.persist(event);
    }

    public List<EventDto> getRecentEvents(Date since, int limit) {
        List<EventLog> events;
        if (since != null) {
            // Get events since timestamp, in chronological order (oldest first)
            events = eventLogRepository.findRecentSince(since, limit);
        } else {
            // Get most recent events in chronological order (oldest first)
            // Frontend will reverse to display newest at top
            events = eventLogRepository.findRecent(limit);
        }
        return events.stream()
                .map(e -> new EventDto(e.id, e.getTicketId(), e.getIncomingRequestId(), e.getEventType(), e.getSeverity(), e.getSource(), e.getMessage(), e.getPayloadJson(), e.getCreatedAt()))
                .collect(Collectors.toList());
    }
}
