package com.example.ticket.model;

import java.util.Arrays;
import java.util.Objects;

public record TicketData(Long ticketId, String ticketType, String text, float[] vector, long timestamp)
        implements MappedTicket {

    public TicketData {
        Objects.requireNonNull(ticketId);
        Objects.requireNonNull(ticketType);
        Objects.requireNonNull(text);
        Objects.requireNonNull(vector);
        vector = Arrays.copyOf(vector, vector.length);
    }

    public float[] vector() {
        return Arrays.copyOf(vector, vector.length);
    }
}
