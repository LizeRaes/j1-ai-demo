package com.medicalappointment.triage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SimilaritySearchRequest {
    @JsonProperty("ticketType")
    private String ticketType;

    @JsonProperty("text")
    private String text;

    @JsonProperty("maxResults")
    private Integer maxResults;

    @JsonProperty("ticketId")
    private Integer ticketId;

    public SimilaritySearchRequest() {
    }

    public SimilaritySearchRequest(String ticketType, String text, Integer maxResults, Integer ticketId) {
        this.ticketType = ticketType;
        this.text = text;
        this.maxResults = maxResults;
        this.ticketId = ticketId;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }
}
