package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpsertRequest(@JsonProperty("ticketId") Long ticketId, @JsonProperty("ticketType") String ticketType,
                            @JsonProperty("text") String text) {
}
