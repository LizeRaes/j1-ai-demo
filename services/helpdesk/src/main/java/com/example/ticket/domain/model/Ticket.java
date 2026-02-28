package com.example.ticket.domain.model;

import com.example.ticket.domain.constants.TicketSource;
import com.example.ticket.domain.constants.TicketStatus;
import com.example.ticket.domain.constants.TicketType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    private Long id;

    private String name;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Lob
    @Column(name = "original_request", nullable = false)
    private String originalRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false, length = 32)
    private TicketType ticketType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private TicketSource source;

    @Column(name = "assigned_team", nullable = false, length = 64)
    private String assignedTeam;

    @Column(name = "assigned_to", length = 64)
    private String assignedTo;

    @Column(name = "urgency_flag", nullable = false)
    private Boolean urgencyFlag;

    @Column(name = "urgency_score")
    private Double urgencyScore;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "rollback_allowed", nullable = false)
    private Boolean rollbackAllowed;

    @Lob
    @Column(name = "ai_payload_json", columnDefinition = "TEXT")
    private String aiPayloadJson;

    @Column(name = "incoming_request_id")
    private Long incomingRequestId;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, updatable = true)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOriginalRequest() {
        return originalRequest;
    }

    public void setOriginalRequest(String originalRequest) {
        this.originalRequest = originalRequest;
    }

    public TicketType getTicketType() {
        return ticketType;
    }

    public void setTicketType(TicketType ticketType) {
        this.ticketType = ticketType;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public TicketSource getSource() {
        return source;
    }

    public void setSource(TicketSource source) {
        this.source = source;
    }

    public String getAssignedTeam() {
        return assignedTeam;
    }

    public void setAssignedTeam(String assignedTeam) {
        this.assignedTeam = assignedTeam;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Boolean getUrgencyFlag() {
        return urgencyFlag;
    }

    public void setUrgencyFlag(Boolean urgencyFlag) {
        this.urgencyFlag = urgencyFlag;
    }

    public Double getUrgencyScore() {
        return urgencyScore;
    }

    public void setUrgencyScore(Double urgencyScore) {
        this.urgencyScore = urgencyScore;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(Double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public Boolean getRollbackAllowed() {
        return rollbackAllowed;
    }

    public void setRollbackAllowed(Boolean rollbackAllowed) {
        this.rollbackAllowed = rollbackAllowed;
    }

    public String getAiPayloadJson() {
        return aiPayloadJson;
    }

    public void setAiPayloadJson(String aiPayloadJson) {
        this.aiPayloadJson = aiPayloadJson;
    }

    public Long getIncomingRequestId() {
        return incomingRequestId;
    }

    public void setIncomingRequestId(Long incomingRequestId) {
        this.incomingRequestId = incomingRequestId;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
