package com.example.aicodingassistant.dto;

public record SubmitJobRequest(
        Long ticketId,
        String originalRequest,
        String repoUrl,
        Double confidenceThreshold,
        String callbackUrl,
        String callbackAuthToken
) {}
