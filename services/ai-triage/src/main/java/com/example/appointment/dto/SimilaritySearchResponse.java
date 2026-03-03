package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class SimilaritySearchResponse {
    @JsonProperty("relatedTicketIds")
    private List<Long> relatedTicketIds;

    public SimilaritySearchResponse() {
        this.relatedTicketIds = new ArrayList<>();
    }

    public List<Long> getRelatedTicketIds() {
        return relatedTicketIds;
    }

    public void setRelatedTicketIds(List<Long> relatedTicketIds) {
        this.relatedTicketIds = relatedTicketIds;
    }
}
