package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TriageRequestDto(@JsonProperty("incomingRequestId") Long incomingRequestId,
                               @JsonProperty("message") String message, @JsonProperty("ticketId") Long ticketId,
                               @JsonProperty("allowedTicketTypes") List<AllowedTicketType> allowedTicketTypes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AllowedTicketType(@JsonProperty("type") String type,
                                    @JsonProperty("description") String description) {
    }

}

