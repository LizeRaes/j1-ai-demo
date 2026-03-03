package com.example.appointment.dto;

import com.example.appointment.domain.JobSubmissionStatus;

public record SubmitJobResponse(
        JobSubmissionStatus status,
        String message
) {}
