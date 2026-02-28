package com.example.aicodingassistant.dto;

import com.example.aicodingassistant.domain.JobSubmissionStatus;

public record SubmitJobResponse(
        String jobId,
        JobSubmissionStatus status,
        String message
) {}
