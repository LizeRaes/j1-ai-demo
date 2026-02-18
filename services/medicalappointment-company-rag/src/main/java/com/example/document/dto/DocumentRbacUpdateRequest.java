package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DocumentRbacUpdateRequest(@JsonProperty("documentName") String documentName,
                                        @JsonProperty("rbacTeams") List<String> rbacTeams) {}
