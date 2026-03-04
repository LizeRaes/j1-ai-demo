package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SimilaritySearchResponse(@JsonProperty("relatedTicketIds") List<Long> relatedTicketIds) {
}

