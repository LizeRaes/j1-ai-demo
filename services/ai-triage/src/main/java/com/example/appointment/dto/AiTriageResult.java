package com.medicalappointment.triage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiTriageResult {
    @JsonProperty("ticketType")
    private String ticketType;

    @JsonProperty("urgencyScore")
    private Integer urgencyScore;

    @JsonProperty("aiConfidencePercent")
    private Integer aiConfidencePercent;

    public AiTriageResult() {
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
}
