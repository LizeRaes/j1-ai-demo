package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SearchResponse {
    @JsonProperty("relatedTicketIds")
    private List<Long> relatedTicketIds;

    public SearchResponse() {
    }

    public SearchResponse(List<Long> relatedTicketIds) {
        this.relatedTicketIds = relatedTicketIds;
    }

    public List<Long> getRelatedTicketIds() {
        return relatedTicketIds;
    }

    public void setRelatedTicketIds(List<Long> relatedTicketIds) {
        this.relatedTicketIds = relatedTicketIds;
    }
}
