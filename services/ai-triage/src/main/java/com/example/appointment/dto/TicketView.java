package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketView(@JsonProperty("ticketId") Long ticketId,
                         @JsonProperty("incomingRequestId") Long incomingRequestId,
                         @JsonProperty("message") String message, @JsonProperty("timestamp") Instant timestamp,
                         @JsonProperty("status") String status, // OK, FAILED
                         @JsonProperty("ticketType") String ticketType,
                         @JsonProperty("urgencyScore") Integer urgencyScore,
                         @JsonProperty("aiConfidencePercent") Integer aiConfidencePercent,
                         @JsonProperty("relatedTicketIds") List<Long> relatedTicketIds,
                         @JsonProperty("policyCitations") List<DocumentSearchResponse.DocumentResult> policyCitations,
                         @JsonProperty("failReason") String failReason) {
}
