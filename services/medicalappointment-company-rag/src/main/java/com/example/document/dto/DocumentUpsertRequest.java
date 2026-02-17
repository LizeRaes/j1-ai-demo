package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DocumentUpsertRequest(@JsonProperty("documentName") String documentName,
                                    @JsonProperty("content") String content,
                                    @JsonProperty("rbacTeams") List<String> rbacTeams) {
}
