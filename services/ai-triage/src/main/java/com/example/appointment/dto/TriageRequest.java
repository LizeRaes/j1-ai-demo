package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TriageRequest(@JsonProperty("incomingRequestId") Long incomingRequestId,
                            @JsonProperty("message") @NotBlank(message = "Message cannot be empty") String message,
                            @JsonProperty("allowedTicketTypes") @NotEmpty(message = "Allowed ticket types cannot be empty") List<@Valid TicketTypeInfo> allowedTicketTypes,
                            @JsonProperty("ticketId") @NotNull(message = "Ticket ID cannot be null") Long ticketId) {

    public record TicketTypeInfo(@JsonProperty("type") @NotBlank(message = "Ticket type cannot be empty") String type,
                                 @JsonProperty("description") @NotBlank(message = "Ticket type description cannot be empty") String description) {
    }
}
