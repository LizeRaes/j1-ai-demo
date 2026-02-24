package com.example.ticket.domain.model;

import com.example.ticket.domain.enums.EventSeverity;
import com.example.ticket.domain.enums.EventType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "event_log")
public class EventLog extends PanacheEntity {
    @Column(name = "ticket_id")
    public Long ticketId;

    @Column(name = "incoming_request_id")
    public Long incomingRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    public EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    public EventSeverity severity;

    @Column(name = "source", nullable = false, length = 64)
    public String source;

    @Column(name = "message", nullable = false, length = 512)
    public String message;

    @Lob
    @Column(name = "payload_json", columnDefinition = "TEXT")
    public String payloadJson;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
