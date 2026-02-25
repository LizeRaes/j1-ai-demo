package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SimilarityUpsertRequestDto(@JsonProperty("id") Long ticketId,
                                         @JsonProperty("type") String ticketType, @JsonProperty("text") String text) {
}
