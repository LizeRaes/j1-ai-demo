package com.example.aicodingassistant.dto;

import com.example.aicodingassistant.domain.JobResultStatus;

public record CallbackResultRequest(
        String jobId,
        long ticketId,
        JobResultStatus status,
        double confidence,
        String prUrl,
        String likelyCause,
        String errorMessage
) {}
