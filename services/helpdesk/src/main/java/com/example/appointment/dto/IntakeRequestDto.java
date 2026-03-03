package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntakeRequestDto(String userId, String persona, String message) {
}
