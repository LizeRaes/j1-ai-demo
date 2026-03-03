package com.example.appointment.dto;

public record TicketPullRequestDto(
        Long id,
        String prUrl,
        Boolean aiGenerated
) {
}
