package com.example.appointment.dto;

public record TicketSyncUpsertRequest(Long ticketId, String ticketType, String text) {
}
