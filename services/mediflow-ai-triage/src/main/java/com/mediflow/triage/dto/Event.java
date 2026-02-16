package com.mediflow.triage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class Event {
    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("level")
    private String level; // INFO, WARN, ERROR

    @JsonProperty("message")
    private String message;

    @JsonProperty("ticketId")
    private Integer ticketId;

    public Event() {
    }

    public Event(String level, String message) {
        this.timestamp = Instant.now();
        this.level = level;
        this.message = message;
    }

    public Event(String level, String message, Integer ticketId) {
        this.timestamp = Instant.now();
        this.level = level;
        this.message = message;
        this.ticketId = ticketId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }
}
