package com.medicalappointment.ticketing.domain.model;

import com.medicalappointment.ticketing.domain.enums.TicketSource;
import com.medicalappointment.ticketing.domain.enums.TicketStatus;
import com.medicalappointment.ticketing.domain.enums.TicketType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tickets")
public class Ticket extends PanacheEntity {
    @Column(name = "user_id", nullable = false, length = 64)
    public String userId;

    @Lob
    @Column(name = "original_request", nullable = false)
    public String originalRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false, length = 32)
    public TicketType ticketType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    public TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    public TicketSource source;

    @Column(name = "assigned_team", nullable = false, length = 64)
    public String assignedTeam;

    @Column(name = "assigned_to", length = 64)
    public String assignedTo;

    @Column(name = "urgency_flag", nullable = false)
    public Boolean urgencyFlag;

    @Column(name = "urgency_score")
    public Double urgencyScore;

    @Column(name = "ai_confidence")
    public Double aiConfidence;

    @Column(name = "rollback_allowed", nullable = false)
    public Boolean rollbackAllowed;

    @Lob
    @Column(name = "ai_payload_json", columnDefinition = "TEXT")
    public String aiPayloadJson;

    @Column(name = "incoming_request_id")
    public Long incomingRequestId;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
