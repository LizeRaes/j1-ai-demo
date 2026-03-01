package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DocumentSearchResponse(@JsonProperty("results") List<DocumentResult> results) {

    public record DocumentResult(@JsonProperty("documentName") String documentName,
                                 @JsonProperty("documentLink") String documentLink,
                                 @JsonProperty("citation") String citation,
                                 @JsonProperty("score") double score,
                                 @JsonProperty("rbacTeams") List<String> rbacTeams) {
    }


}
