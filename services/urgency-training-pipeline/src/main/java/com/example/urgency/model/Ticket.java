package com.example.urgency.model;

/**
 * Ticket record for training. Demo data format - may change when real data arrives.
 */
public record Ticket(String id, String text, double urgency) {
}
