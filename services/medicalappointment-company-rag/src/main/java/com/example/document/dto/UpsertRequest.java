package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpsertRequest {
    @JsonProperty("ticketId")
    private Long ticketId;

    @JsonProperty("ticketType")
    private String ticketType;

    @JsonProperty("text")
    private String text;

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
