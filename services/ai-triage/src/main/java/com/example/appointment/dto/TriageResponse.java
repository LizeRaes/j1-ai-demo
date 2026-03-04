package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TriageResponse(
        @JsonProperty("status") String status,
        @JsonProperty("ticketType") String ticketType,
        @JsonProperty("urgencyScore") Integer urgencyScore,
        @JsonProperty("aiConfidencePercent") Integer aiConfidencePercent,
        @JsonProperty("relatedTicketIds") List<Long> relatedTicketIds,
        @JsonProperty("policyCitations") List<DocumentSearchResponse.DocumentResult> policyCitations,
        @JsonProperty("failReason") String failReason
) {
}