package com.mediflow.triage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolicyCitation {
    @JsonProperty("documentName")
    private String documentName;

    @JsonProperty("documentLink")
    private String documentLink;

    @JsonProperty("citation")
    private String citation;

    @JsonProperty("score")
    private Double score;

    @JsonProperty("rbacTeams")
    private List<String> rbacTeams;

    public PolicyCitation() {
    }

    public PolicyCitation(DocumentResult documentResult) {
        this.documentName = documentResult.getDocumentName();
        this.documentLink = documentResult.getDocumentLink();
        this.citation = documentResult.getCitation();
        this.score = documentResult.getScore();
        this.rbacTeams = documentResult.getRbacTeams();
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

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public List<String> getRbacTeams() {
        return rbacTeams;
    }

    public void setRbacTeams(List<String> rbacTeams) {
        this.rbacTeams = rbacTeams;
    }
}
