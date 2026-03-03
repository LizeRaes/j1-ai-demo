package com.example.ticket.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TicketStore {

	public record TicketData(Long ticketId, String ticketType, String text, float[] vector, long timestamp) { }

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

}
