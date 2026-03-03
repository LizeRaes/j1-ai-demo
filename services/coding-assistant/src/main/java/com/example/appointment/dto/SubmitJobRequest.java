package com.example.appointment.dto;

public record SubmitJobRequest(
        Long ticketId,
        String originalRequest,
        String repoUrl,
        Double confidenceThreshold
) {}
