package com.mediflow.triage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocumentSearchRequest {
    @JsonProperty("originalText")
    private String originalText;

    @JsonProperty("maxResults")
    private Integer maxResults;

    @JsonProperty("minScore")
    private Double minScore;

    public DocumentSearchRequest() {
    }

    public DocumentSearchRequest(String originalText, Integer maxResults, Double minScore) {
        this.originalText = originalText;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
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
}
