package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiTriageResult(
        @JsonProperty("ticketType") String ticketType,
        @JsonProperty("urgencyScore") Integer urgencyScore,
        @JsonProperty("aiConfidencePercent") Integer aiConfidencePercent
) {
}