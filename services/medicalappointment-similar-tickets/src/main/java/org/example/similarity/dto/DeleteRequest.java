package org.example.similarity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeleteRequest(@JsonProperty("ticketId") Long ticketId) {
}
