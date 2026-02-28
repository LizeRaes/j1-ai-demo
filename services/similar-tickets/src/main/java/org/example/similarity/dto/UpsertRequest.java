package org.example.similarity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpsertRequest(@JsonProperty("ticketId") Long ticketId, @JsonProperty("ticketType") String ticketType,
                            @JsonProperty("text") String text) {
}
