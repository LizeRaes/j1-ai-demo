package com.example.aicodingassistant.dto;

import com.example.aicodingassistant.domain.JobSubmissionStatus;

public record SubmitJobResponse(
        JobSubmissionStatus status,
        String message
) {}
