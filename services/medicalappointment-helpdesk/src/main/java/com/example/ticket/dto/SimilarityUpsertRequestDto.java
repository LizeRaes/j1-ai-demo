package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SimilarityUpsertRequestDto {
    @JsonProperty("id")
    public Long ticketId;

    @JsonProperty("type")
    public String ticketType;

    @JsonProperty("text")
    public String text;
}
