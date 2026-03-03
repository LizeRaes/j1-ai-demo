package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateIncomingRequestDto(String userId, String channel, String rawText) {
}
