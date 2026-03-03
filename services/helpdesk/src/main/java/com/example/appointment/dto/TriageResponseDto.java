package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TriageResponseDto(@JsonProperty("status") String status,
                                @JsonProperty("ticketType") String ticketType,
                                @JsonProperty("urgencyScore") Integer urgencyScore,
                                @JsonProperty("aiConfidencePercent") Integer aiConfidencePercent,
                                @JsonProperty("relatedTicketIds") List<Long> relatedTicketIds,
                                @JsonProperty("policyCitations") List<PolicyCitation> policyCitations,
                                @JsonProperty("failReason") String failReason) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolicyCitation(@JsonProperty("documentName") String documentName,
                                 @JsonProperty("documentLink") String documentLink,
                                 @JsonProperty("citation") String citation,
                                 @JsonProperty("score") Double score,
                                 @JsonProperty("rbacTeams") List<String> rbacTeams) {
    }
}
