package com.example.ticket.domain.model;

import com.example.ticket.domain.constants.EventSeverity;
import com.example.ticket.domain.constants.EventType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_logs")
public class EventLog {

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "incoming_request_id")
    private Long incomingRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private EventSeverity severity;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "message", nullable = false, length = 512)
    private String message;

    @Lob
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getIncomingRequestId() {
        return incomingRequestId;
    }

    public void setIncomingRequestId(Long incomingRequestId) {
        this.incomingRequestId = incomingRequestId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public EventSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(EventSeverity severity) {
        this.severity = severity;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


}
