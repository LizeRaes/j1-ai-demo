package com.mediflow.ticketing.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_comments")
public class TicketComment extends PanacheEntity {
    @Column(name = "ticket_id", nullable = false)
    public Long ticketId;

    @Column(name = "author_id", nullable = false, length = 64)
    public String authorId;

    @Lob
    @Column(name = "body", nullable = false)
    public String body;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
