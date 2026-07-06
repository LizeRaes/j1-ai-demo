package com.example.ticket.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.helidon.service.registry.Service;

import com.example.ticket.model.TicketData;

@Service.Singleton
public class TicketStore {

    private final Map<Long, TicketData> tickets = new ConcurrentHashMap<>();

    public void storeTicket(TicketData ticket) {
        tickets.put(ticket.ticketId(), ticket);
    }

    public void removeTicket(Long ticketId) {
        tickets.remove(ticketId);
    }

    public List<TicketData> getAllTickets() {
        return tickets.values().stream()
                .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
                .collect(Collectors.toList());
    }

}
