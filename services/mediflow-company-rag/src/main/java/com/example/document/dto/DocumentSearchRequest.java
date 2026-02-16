package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocumentSearchRequest {
    @JsonProperty("originalText")
    private String originalText;

    @JsonProperty("maxResults")
    private Integer maxResults;

    @JsonProperty("minScore")
    private Double minScore;

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
