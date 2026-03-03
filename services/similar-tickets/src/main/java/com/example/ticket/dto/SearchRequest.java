package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchRequest(@JsonProperty("ticketType") String ticketType, @JsonProperty("text") String text,
                            @JsonProperty("maxResults") Integer maxResults, @JsonProperty("minScore") Double minScore,
                            @JsonProperty("ticketId") Long ticketId) {
}
