package com.mediflow.triage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriageResponse {
    @JsonProperty("status")
    private String status;

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

    public TriageResponse() {
        this.relatedTicketIds = new ArrayList<>();
        this.policyCitations = new ArrayList<>();
    }

    public static TriageResponse success(String ticketType, Integer urgencyScore, Integer aiConfidencePercent) {
        TriageResponse response = new TriageResponse();
        response.status = "OK";
        response.ticketType = ticketType;
        response.urgencyScore = urgencyScore;
        response.aiConfidencePercent = aiConfidencePercent;
        return response;
    }

    public static TriageResponse failed(String failReason) {
        TriageResponse response = new TriageResponse();
        response.status = "FAILED";
        response.failReason = failReason;
        return response;
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
