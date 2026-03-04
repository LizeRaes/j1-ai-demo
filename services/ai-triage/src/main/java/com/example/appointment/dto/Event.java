package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record Event(@JsonProperty("timestamp") Instant timestamp,
                    @JsonProperty("level") String level, // INFO, WARN, ERROR
                    @JsonProperty("message") String message,
                    @JsonProperty("ticketId") Long ticketId) {
}
