package com.example.appointment.service;

import com.example.appointment.dto.Event;
import com.example.appointment.dto.TicketView;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class EventLogService {

    private static final int MAX_EVENTS = 1000;
    private static final int MAX_TICKETS = 500;
    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final List<TicketView> tickets = new CopyOnWriteArrayList<>();

    public void addEvent(Event event) {
        synchronized (events) {
            events.addFirst(event); // Add to beginning (newest first)
            if (events.size() > MAX_EVENTS) {
                events.removeLast(); // Remove oldest
            }
        }
    }

    public void addEvent(String level, String message) {
        addEvent(new Event(Instant.now(), level, message, 0L));
    }

    public void addEvent(String level, String message, Long ticketId) {
        addEvent(new Event(Instant.now(), level, message, ticketId));
    }

    public List<Event> getEvents() {
        return List.copyOf(events);
    }

    public void addTicket(TicketView ticket) {
        synchronized (tickets) {
            tickets.addFirst(ticket); // Add to beginning (newest first)
            if (tickets.size() > MAX_TICKETS) {
                tickets.removeLast(); // Remove oldest
            }
        }
    }

    public List<TicketView> getTickets() {
        return List.copyOf(tickets);
    }
}
