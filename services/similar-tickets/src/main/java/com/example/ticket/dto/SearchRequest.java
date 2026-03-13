package com.example.ticket.dto;

public record SearchRequest(String ticketType, String text,
                            Integer maxResults, Double minScore,
                            Long ticketId) {
}
