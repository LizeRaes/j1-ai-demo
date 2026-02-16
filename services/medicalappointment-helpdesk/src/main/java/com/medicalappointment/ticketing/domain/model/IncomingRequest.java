package com.medicalappointment.ticketing.domain.model;

import com.medicalappointment.ticketing.domain.enums.RequestStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "incoming_requests")
public class IncomingRequest extends PanacheEntity {
    @Column(name = "user_id", nullable = false, length = 64)
    public String userId;

    @Column(name = "channel", nullable = false, length = 32)
    public String channel;

    @Lob
    @Column(name = "raw_text", nullable = false)
    public String rawText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    public RequestStatus status;

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
