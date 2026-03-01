package org.example.similarity.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TicketStoreTest {

    @Test
    void testStoreTicket() {
        TicketStore ticketStore = new TicketStore();
        Long ticketId = 1L;
        String ticketType = "test-type";
        String text = "test-text";
        float[] vector = {1.0f, 2.0f};

        ticketStore.storeTicket(ticketId, ticketType, text, vector);

        List<TicketStore.TicketData> tickets = ticketStore.getAllTickets();
        assertEquals(1, tickets.size());
        TicketStore.TicketData ticketData = tickets.getFirst();
        assertEquals(ticketId, ticketData.ticketId());
        assertEquals(ticketType, ticketData.ticketType());
        assertEquals(text, ticketData.text());
        assertArrayEquals(vector, ticketData.vector());
        assertTrue(ticketData.timestamp() > 0);
    }

    @Test
    void testRemoveTicket() {
        TicketStore ticketStore = new TicketStore();
        Long ticketId = 1L;
        String ticketType = "test-type";
        String text = "test-text";
        float[] vector = {1.0f, 2.0f};

        ticketStore.storeTicket(ticketId, ticketType, text, vector);
        assertEquals(1, ticketStore.getAllTickets().size());

        ticketStore.removeTicket(ticketId);
        assertEquals(0, ticketStore.getAllTickets().size());
    }

    @Test
    void testGetAllTickets() {
        TicketStore ticketStore = new TicketStore();
        Long ticketId1 = 1L;
        Long ticketId2 = 2L;
        String ticketType = "test-type";
        String text = "test-text";
        float[] vector = {1.0f, 2.0f};

        ticketStore.storeTicket(ticketId1, ticketType, text, vector);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ticketStore.storeTicket(ticketId2, ticketType, text, vector);

        List<TicketStore.TicketData> tickets = ticketStore.getAllTickets();
        assertEquals(2, tickets.size());
        assertEquals(ticketId2, tickets.get(0).ticketId()); // Latest first
        assertEquals(ticketId1, tickets.get(1).ticketId());
    }

    @Test
    void testGetAllTicketsEmpty() {
        TicketStore ticketStore = new TicketStore();
        List<TicketStore.TicketData> tickets = ticketStore.getAllTickets();
        assertTrue(tickets.isEmpty());
    }

    @Test
    void testRemoveNonExistentTicket() {
        TicketStore ticketStore = new TicketStore();
        Long ticketId = 1L;
        ticketStore.removeTicket(ticketId);
        assertTrue(ticketStore.getAllTickets().isEmpty());
    }

    @Test
    void testStoreMultipleTickets() {
        TicketStore ticketStore = new TicketStore();
        Long ticketId1 = 1L;
        Long ticketId2 = 2L;
        String ticketType = "test-type";
        String text = "test-text";
        float[] vector = {1.0f, 2.0f};

        ticketStore.storeTicket(ticketId1, ticketType, text, vector);
        ticketStore.storeTicket(ticketId2, ticketType, text, vector);

        List<TicketStore.TicketData> tickets = ticketStore.getAllTickets();
        assertEquals(2, tickets.size());
    }

    @Test
    void testStoreSameTicketMultipleTimes() {
        TicketStore ticketStore = new TicketStore();
        Long ticketId = 1L;
        String ticketType = "test-type";
        String text = "test-text";
        float[] vector = {1.0f, 2.0f};

        ticketStore.storeTicket(ticketId, ticketType, text, vector);
        ticketStore.storeTicket(ticketId, ticketType, text, vector);

        List<TicketStore.TicketData> tickets = ticketStore.getAllTickets();
        assertEquals(1, tickets.size());
        TicketStore.TicketData ticketData = tickets.getFirst();
        assertTrue(ticketData.timestamp() > 0);
    }
}
