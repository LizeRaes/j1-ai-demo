package com.example.appointment.service;

import com.example.appointment.dto.Event;
import com.example.appointment.dto.TicketView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EventLogServiceTest {

    @Test
    void addEventShouldStoreNewestFirst() {
        EventLogService service = new EventLogService();

        service.addEvent(new Event(Instant.parse("2026-01-01T00:00:00Z"), "INFO", "first", 1L));
        service.addEvent(new Event(Instant.parse("2026-01-01T00:00:01Z"), "WARN", "second", 2L));

        List<Event> events = service.getEvents();
        assertEquals(2, events.size());
        assertEquals("second", events.get(0).message());
        assertEquals("first", events.get(1).message());
    }

    @Test
    void addEventShouldPopulateFields() {
        EventLogService service = new EventLogService();

        service.addEvent("ERROR", "failure", 99L);

        Event event = service.getEvents().getFirst();
        assertEquals("ERROR", event.level());
        assertEquals("failure", event.message());
        assertEquals(99L, event.ticketId());
        assertNotNull(event.timestamp());
    }

    @Test
    void addTicketShouldStoreNewestFirst() {
        EventLogService service = new EventLogService();

        TicketView first = new TicketView(1L, 10L, "first", Instant.parse("2026-01-01T00:00:00Z"),
                "OK", "billing", 3, 80, List.of(1L), List.of(), null);
        TicketView second = new TicketView(2L, 20L, "second", Instant.parse("2026-01-01T00:00:01Z"),
                "FAILED", null, null, null, List.of(), List.of(), "oops");

        service.addTicket(first);
        service.addTicket(second);

        List<TicketView> tickets = service.getTickets();
        assertEquals(2, tickets.size());
        assertEquals(2L, tickets.get(0).ticketId());
        assertEquals(1L, tickets.get(1).ticketId());
    }

    @Test
    void getEventsShouldReturnImmutableCopy() {
        EventLogService service = new EventLogService();
        service.addEvent("INFO", "immutable-check", 1L);

        List<Event> events = service.getEvents();

        assertThrows(UnsupportedOperationException.class,
                () -> events.add(new Event(Instant.now(), "INFO", "x", 2L)));
    }
}