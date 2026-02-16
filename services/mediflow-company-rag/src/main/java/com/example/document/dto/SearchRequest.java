package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SearchRequest {
    @JsonProperty("ticketType")
    private String ticketType;

    @JsonProperty("text")
    private String text;

    @JsonProperty("maxResults")
    private Integer maxResults;

    @JsonProperty("minScore")
    private Double minScore;

    @JsonProperty("ticketId")
    private Long ticketId;

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

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
