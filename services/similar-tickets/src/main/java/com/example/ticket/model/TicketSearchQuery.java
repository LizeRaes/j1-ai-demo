package com.example.ticket.model;

import java.util.Objects;

public record TicketSearchQuery(String text, int maxResults, double minScore, Long excludeTicketId)
        implements MappedTicketSearchQuery {

    public TicketSearchQuery {
        Objects.requireNonNull(text);
        Objects.requireNonNull(excludeTicketId);
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be greater than zero");
        }
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be between 0.0 and 1.0");
        }
    }
}
