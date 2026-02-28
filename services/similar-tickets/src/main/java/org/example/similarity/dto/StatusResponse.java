package org.example.similarity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatusResponse(@JsonProperty("status") String status) {
}
