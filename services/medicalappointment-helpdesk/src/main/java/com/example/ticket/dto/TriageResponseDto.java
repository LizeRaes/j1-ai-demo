package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TriageResponseDto(@JsonProperty("status") String status,
                                @JsonProperty("ticketType") String ticketType,
                                @JsonProperty("urgencyScore") Integer urgencyScore,
                                @JsonProperty("aiConfidencePercent") Integer aiConfidencePercent,
                                @JsonProperty("relatedTicketIds") List<Long> relatedTicketIds,
                                @JsonProperty("policyCitations") List<PolicyCitation> policyCitations,
                                @JsonProperty("failReason") String failReason) {

    public record PolicyCitation(@JsonProperty("documentName") String documentName,
                                 @JsonProperty("documentLink") String documentLink,
                                 @JsonProperty("citation") String citation,
                                 @JsonProperty("score") Double score,
                                 @JsonProperty("rbacTeams") List<String> rbacTeams) {
    }
}
