package com.example.ticket.dto;

public record CreateIncomingRequestDto(String userId, String channel, String rawText) {
}
