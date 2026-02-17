package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DocumentsResponse(@JsonProperty("documents") List<DocumentInfo> documents) {

    public record DocumentInfo(@JsonProperty("documentName") String documentName,
                               @JsonProperty("documentLink") String documentLink,
                               @JsonProperty("rbacTeams") List<String> rbacTeams) {

    }
}
