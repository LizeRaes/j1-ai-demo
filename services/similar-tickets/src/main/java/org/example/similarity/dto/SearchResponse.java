package org.example.similarity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchResponse(@JsonProperty("relatedTicketIds") List<Long> relatedTicketIds) {
}
