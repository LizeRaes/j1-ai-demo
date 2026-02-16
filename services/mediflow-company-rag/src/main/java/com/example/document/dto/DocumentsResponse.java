package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DocumentsResponse {
    @JsonProperty("documents")
    private List<DocumentInfo> documents;

    public DocumentsResponse() {
    }

    public DocumentsResponse(List<DocumentInfo> documents) {
        this.documents = documents;
    }

    public List<DocumentInfo> getDocuments() {
        return documents;
    }

    public void setDocuments(List<DocumentInfo> documents) {
        this.documents = documents;
    }

    public static class DocumentInfo {
        @JsonProperty("documentName")
        private String documentName;

        @JsonProperty("documentLink")
        private String documentLink;

        @JsonProperty("rbacTeams")
        private List<String> rbacTeams;

        public DocumentInfo() {
        }

        public DocumentInfo(String documentName, String documentLink, List<String> rbacTeams) {
            this.documentName = documentName;
            this.documentLink = documentLink;
            this.rbacTeams = rbacTeams;
        }

        public String getDocumentName() {
            return documentName;
        }

        public void setDocumentName(String documentName) {
            this.documentName = documentName;
        }

        public String getDocumentLink() {
            return documentLink;
        }

        public void setDocumentLink(String documentLink) {
            this.documentLink = documentLink;
        }

        public List<String> getRbacTeams() {
            return rbacTeams;
        }

        public void setRbacTeams(List<String> rbacTeams) {
            this.rbacTeams = rbacTeams;
        }
    }
}
