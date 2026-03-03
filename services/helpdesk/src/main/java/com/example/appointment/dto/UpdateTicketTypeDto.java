package com.example.appointment.dto;

import com.example.appointment.domain.constants.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTicketTypeDto(TicketType ticketType) {
}
