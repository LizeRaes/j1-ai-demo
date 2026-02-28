package com.example.aicodingassistant.domain;

import com.example.aicodingassistant.dto.SubmitJobRequest;

public record JobContext(
        String jobId,
        SubmitJobRequest request
) {}
