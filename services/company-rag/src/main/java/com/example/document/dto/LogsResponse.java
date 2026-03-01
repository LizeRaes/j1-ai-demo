package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LogsResponse(@JsonProperty("logs") List<LogInfo> logs) {

    public record LogInfo(@JsonProperty("message") String message,
                          @JsonProperty("type") String type,
                          @JsonProperty("timestamp") long timestamp) {
    }

}
