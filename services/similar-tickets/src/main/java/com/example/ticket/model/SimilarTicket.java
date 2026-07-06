package com.example.ticket.model;

import java.util.Objects;

public record SimilarTicket(Long ticketId, double score) {

    public SimilarTicket {
        Objects.requireNonNull(ticketId);
    }
}
