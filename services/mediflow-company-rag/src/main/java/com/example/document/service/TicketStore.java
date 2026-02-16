package com.example.document.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class TicketStore {

    // Store tickets by ticketId: ticketId -> {ticketId, ticketType, text, vector, timestamp}
    private final Map<Long, TicketData> tickets = new ConcurrentHashMap<>();

    public void storeTicket(Long ticketId, String ticketType, String text, float[] vector) {
        tickets.put(ticketId, new TicketData(ticketId, ticketType, text, vector, System.currentTimeMillis()));
    }

    public void removeTicket(Long ticketId) {
        tickets.remove(ticketId);
    }

    public List<TicketData> getAllTickets() {
        return tickets.values().stream()
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp)) // Latest first
                .collect(Collectors.toList());
    }

    public static class TicketData {
        public final Long ticketId;
        public final String ticketType;
        public final String text;
        public final float[] vector;
        public final long timestamp;

        public TicketData(Long ticketId, String ticketType, String text, float[] vector, long timestamp) {
            this.ticketId = ticketId;
            this.ticketType = ticketType;
            this.text = text;
            this.vector = vector;
            this.timestamp = timestamp;
        }
    }
}
