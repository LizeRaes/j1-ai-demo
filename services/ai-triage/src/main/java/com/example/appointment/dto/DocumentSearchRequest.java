package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentSearchRequest(@JsonProperty("originalText") String originalText,
                                    @JsonProperty("maxResults") Integer maxResults,
                                    @JsonProperty("minScore") Double minScore) {

}
