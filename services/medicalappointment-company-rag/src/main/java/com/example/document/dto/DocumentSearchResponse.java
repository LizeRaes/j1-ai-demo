package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DocumentSearchResponse {
    @JsonProperty("results")
    private List<DocumentResult> results;

    public DocumentSearchResponse() {
    }

    public DocumentSearchResponse(List<DocumentResult> results) {
        this.results = results;
    }

    public List<DocumentResult> getResults() {
        return results;
    }

    public void setResults(List<DocumentResult> results) {
        this.results = results;
    }

    public static class DocumentResult {
        @JsonProperty("documentName")
        private String documentName;

        @JsonProperty("documentLink")
        private String documentLink;

        @JsonProperty("citation")
        private String citation;

        @JsonProperty("score")
        private double score;

        @JsonProperty("rbacTeams")
        private List<String> rbacTeams;

        public DocumentResult() {
        }

        public DocumentResult(String documentName, String documentLink, String citation,
                             double score, List<String> rbacTeams) {
            this.documentName = documentName;
            this.documentLink = documentLink;
            this.citation = citation;
            this.score = score;
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

        public String getCitation() {
            return citation;
        }

        public void setCitation(String citation) {
            this.citation = citation;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public List<String> getRbacTeams() {
            return rbacTeams;
        }

        public void setRbacTeams(List<String> rbacTeams) {
            this.rbacTeams = rbacTeams;
        }
    }
}
