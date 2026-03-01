package com.medicalappointment.triage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TicketView {
    @JsonProperty("ticketId")
    private Integer ticketId;

    @JsonProperty("incomingRequestId")
    private Long incomingRequestId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("status")
    private String status; // OK, FAILED

    @JsonProperty("ticketType")
    private String ticketType;

    @JsonProperty("urgencyScore")
    private Integer urgencyScore;

    @JsonProperty("aiConfidencePercent")
    private Integer aiConfidencePercent;

    @JsonProperty("relatedTicketIds")
    private List<Long> relatedTicketIds;

    @JsonProperty("policyCitations")
    private List<PolicyCitation> policyCitations;

    @JsonProperty("failReason")
    private String failReason;

    public TicketView() {
        this.relatedTicketIds = new ArrayList<>();
        this.policyCitations = new ArrayList<>();
        this.timestamp = Instant.now();
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }

    public Long getIncomingRequestId() {
        return incomingRequestId;
    }

    public void setIncomingRequestId(Long incomingRequestId) {
        this.incomingRequestId = incomingRequestId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public Integer getUrgencyScore() {
        return urgencyScore;
    }

    public void setUrgencyScore(Integer urgencyScore) {
        this.urgencyScore = urgencyScore;
    }

    public Integer getAiConfidencePercent() {
        return aiConfidencePercent;
    }

    public void setAiConfidencePercent(Integer aiConfidencePercent) {
        this.aiConfidencePercent = aiConfidencePercent;
    }

    public List<Long> getRelatedTicketIds() {
        return relatedTicketIds;
    }

    public void setRelatedTicketIds(List<Long> relatedTicketIds) {
        this.relatedTicketIds = relatedTicketIds;
    }

    public List<PolicyCitation> getPolicyCitations() {
        return policyCitations;
    }

    public void setPolicyCitations(List<PolicyCitation> policyCitations) {
        this.policyCitations = policyCitations;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}
