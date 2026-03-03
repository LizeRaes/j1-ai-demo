package com.example.ticket.dto;

import com.example.ticket.domain.constants.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTicketTypeDto(TicketType ticketType) {
}
