package com.mediflow.ticketing.service;

import com.mediflow.ticketing.domain.enums.EventSeverity;
import com.mediflow.ticketing.domain.enums.EventType;
import com.mediflow.ticketing.domain.model.EventLog;
import com.mediflow.ticketing.mapper.EventMapper;
import com.mediflow.ticketing.persistence.EventLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class EventService {
    @Inject
    EventLogRepository eventLogRepository;

    @Inject
    EventMapper eventMapper;

    @Transactional
    public void logEvent(EventType eventType, EventSeverity severity, String source, String message, Long ticketId, Long incomingRequestId, String payloadJson) {
        EventLog event = new EventLog();
        event.eventType = eventType;
        event.severity = severity;
        event.source = source;
        event.message = message;
        event.ticketId = ticketId;
        event.incomingRequestId = incomingRequestId;
        event.payloadJson = payloadJson;
        eventLogRepository.persist(event);
    }

    public List<com.mediflow.ticketing.dto.EventDto> getRecentEvents(OffsetDateTime since, int limit) {
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
            .map(eventMapper::toDto)
            .collect(Collectors.toList());
    }
}
