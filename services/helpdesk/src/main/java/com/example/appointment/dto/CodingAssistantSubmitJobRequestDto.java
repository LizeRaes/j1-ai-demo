package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CodingAssistantSubmitJobRequestDto(
        Long ticketId,
        String originalRequest,
        String repoUrl,
        Double confidenceThreshold
) {
}
