package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocumentDeleteRequest {
    @JsonProperty("documentName")
    private String documentName;

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }
}
