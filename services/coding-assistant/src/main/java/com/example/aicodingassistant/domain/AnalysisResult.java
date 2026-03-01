package com.example.aicodingassistant.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResult(
        String likelyCause,
        double confidence
) {}
