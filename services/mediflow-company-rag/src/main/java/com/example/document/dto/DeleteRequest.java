package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteRequest {
    @JsonProperty("ticketId")
    private Long ticketId;

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
