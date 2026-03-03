package com.example.appointment.service;

import com.example.appointment.dto.Event;
import com.example.appointment.dto.TicketView;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class EventLogService {

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final List<TicketView> tickets = new CopyOnWriteArrayList<>();
    private static final int MAX_EVENTS = 1000;
    private static final int MAX_TICKETS = 500;

    public void addEvent(Event event) {
        synchronized (events) {
            events.add(0, event); // Add to beginning (newest first)
            if (events.size() > MAX_EVENTS) {
                events.remove(events.size() - 1); // Remove oldest
            }
        }
    }

    public void addEvent(String level, String message) {
        addEvent(new Event(level, message));
    }

    public void addEvent(String level, String message, Integer ticketId) {
        addEvent(new Event(level, message, ticketId));
    }

    public List<Event> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public void addTicket(TicketView ticket) {
        synchronized (tickets) {
            tickets.add(0, ticket); // Add to beginning (newest first)
            if (tickets.size() > MAX_TICKETS) {
                tickets.remove(tickets.size() - 1); // Remove oldest
            }
        }
    }

    public List<TicketView> getTickets() {
        return Collections.unmodifiableList(new ArrayList<>(tickets));
    }
}
