package com.example.ticket.dto;

import com.example.ticket.domain.constants.RequestStatus;

import java.time.LocalDateTime;

public record IncomingRequestDto(Long id, String userId, String channel, String rawText, RequestStatus status,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
}
