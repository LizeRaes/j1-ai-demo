package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TicketsResponse(@JsonProperty("tickets") List<TicketInfo> tickets) {

    public record TicketInfo(@JsonProperty("ticketId") Long id, @JsonProperty("ticketType") String type,
                             @JsonProperty("text") String text, @JsonProperty("vector") float[] vector) {
    }
}
