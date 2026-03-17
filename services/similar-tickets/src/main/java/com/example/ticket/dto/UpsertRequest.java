package com.example.ticket.dto;

public record UpsertRequest(Long ticketId,
                            String ticketType,
                            String text) {
}
