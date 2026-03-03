package com.example.appointment.domain;

import com.example.appointment.dto.SubmitJobRequest;

public record JobContext(
        String jobId,
        SubmitJobRequest request
) {}
