package com.example.appointment.dto;

public record CallbackResultRequest(
        long ticketId,
        String prUrl
) {}
