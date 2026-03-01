package com.example.ticket.dto;

public record TicketPullRequestDto(
        Long id,
        String prUrl,
        Boolean aiGenerated
) {
}
