package com.example.ticket.dto;

import com.example.ticket.domain.enums.RequestStatus;

import java.util.Date;

public record IncomingRequestDto (Long id, String userId, String channel, String rawText, RequestStatus status,
                                  Date createdAt, Date updatedAt) {
}
