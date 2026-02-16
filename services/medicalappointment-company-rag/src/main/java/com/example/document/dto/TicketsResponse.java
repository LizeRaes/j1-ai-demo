package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TicketsResponse {
    @JsonProperty("tickets")
    private List<TicketInfo> tickets;

    public TicketsResponse() {
    }

    public TicketsResponse(List<TicketInfo> tickets) {
        this.tickets = tickets;
    }

    public List<TicketInfo> getTickets() {
        return tickets;
    }

    public void setTickets(List<TicketInfo> tickets) {
        this.tickets = tickets;
    }

    public static class TicketInfo {
        @JsonProperty("ticketId")
        private Long ticketId;

        @JsonProperty("ticketType")
        private String ticketType;

        @JsonProperty("text")
        private String text;

        @JsonProperty("vector")
        private float[] vector;

        public TicketInfo() {
        }

        public TicketInfo(Long ticketId, String ticketType, String text, float[] vector) {
            this.ticketId = ticketId;
            this.ticketType = ticketType;
            this.text = text;
            this.vector = vector;
        }

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

        public float[] getVector() {
            return vector;
        }

        public void setVector(float[] vector) {
            this.vector = vector;
        }
    }
}
