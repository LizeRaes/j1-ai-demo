package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DocumentSearchResponse {
    @JsonProperty("results")
    private List<DocumentResult> results;

    public DocumentSearchResponse() {
    }

    public List<DocumentResult> getResults() {
        return results;
    }

    public void setResults(List<DocumentResult> results) {
        this.results = results;
    }
}
