package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatusResponse(@JsonProperty("status") String status) {
}
