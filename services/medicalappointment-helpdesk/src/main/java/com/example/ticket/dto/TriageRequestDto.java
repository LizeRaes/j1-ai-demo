package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TriageRequestDto(@JsonProperty("incomingRequestId") Long incomingRequestId,
                               @JsonProperty("message") String message, @JsonProperty("ticketId") Long ticketId,
                               @JsonProperty("allowedTicketTypes") List<AllowedTicketType> allowedTicketTypes) {

    public record AllowedTicketType(@JsonProperty("type") String type,
                                    @JsonProperty("description") String description) {
    }

}

