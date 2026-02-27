package com.example.ticket.dto;

import com.example.ticket.domain.constants.TicketStatus;

public record UpdateTicketStatusDto(TicketStatus status) {
}
