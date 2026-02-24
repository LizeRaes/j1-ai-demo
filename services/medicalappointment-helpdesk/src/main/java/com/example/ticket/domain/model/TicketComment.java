package com.example.ticket.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Date;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_comments")
public class TicketComment extends PanacheEntity {
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;

    @Lob
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private Date createdAt;

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
