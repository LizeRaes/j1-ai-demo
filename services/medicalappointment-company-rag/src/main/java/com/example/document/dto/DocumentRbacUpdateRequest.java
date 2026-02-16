package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DocumentRbacUpdateRequest {
    @JsonProperty("documentName")
    private String documentName;

    @JsonProperty("rbacTeams")
    private List<String> rbacTeams;

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public List<String> getRbacTeams() {
        return rbacTeams;
    }

    public void setRbacTeams(List<String> rbacTeams) {
        this.rbacTeams = rbacTeams;
    }
}
