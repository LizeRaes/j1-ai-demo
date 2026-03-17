package com.example.ticket.dto;

import java.util.List;

import jakarta.json.bind.annotation.JsonbProperty;

public record TicketsResponse(List<TicketInfo> tickets) {

    public record TicketInfo(@JsonbProperty("ticketId") Long id,
                             @JsonbProperty("ticketType") String type,
                             String text,
                             float[] vector) {
    }
}
