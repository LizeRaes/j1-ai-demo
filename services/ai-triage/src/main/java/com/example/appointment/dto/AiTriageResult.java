package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

public record AiTriageResult(
        @JsonProperty("ticketType")
        @Description("Ticket type from the allowed list. Choose *_OTHER if unsure about category, or OTHER if unsure about group.")
        String ticketType,
        // MCP variant only:
        // @JsonProperty("urgencyScore")
        // @Description("Urgency Score between 1 and 10")
        // Integer urgencyScore,
        @JsonProperty("aiConfidencePercent")
        @Description("Confidence 0 - 100 in the ticket type classification.")
        Integer aiConfidencePercent
) {
}