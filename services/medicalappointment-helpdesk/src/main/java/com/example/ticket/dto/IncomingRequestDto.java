package com.example.ticket.dto;

import com.example.ticket.domain.constants.RequestStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncomingRequestDto(Long id, String userId, String channel, String rawText, RequestStatus status,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
}
