package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CodingAssistantSubmitJobResponseDto(
        String status,
        String message
) {
}
