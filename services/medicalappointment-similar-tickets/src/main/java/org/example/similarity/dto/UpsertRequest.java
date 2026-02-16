package org.example.similarity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpsertRequest(@JsonProperty("id") Long ticketId, @JsonProperty("type") String ticketType,
                            @JsonProperty("text") String text) {
}
