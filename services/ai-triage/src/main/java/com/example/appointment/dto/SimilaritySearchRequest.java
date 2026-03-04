package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SimilaritySearchRequest(@JsonProperty("ticketType") String ticketType, @JsonProperty("text") String text,
                                      @JsonProperty("maxResults") Integer maxResults,
                                      @JsonProperty("minScore") Double minScore,
                                      @JsonProperty("ticketId") Long ticketId) {
}