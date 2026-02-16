package org.example.similarity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchRequest(@JsonProperty("type") String ticketType, @JsonProperty("text") String text,
                            @JsonProperty("maxResults") Integer maxResults, @JsonProperty("minScore") Double minScore,
                            @JsonProperty("id") Long ticketId) {
}
