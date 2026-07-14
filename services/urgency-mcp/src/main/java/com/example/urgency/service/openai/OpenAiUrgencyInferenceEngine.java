package com.example.urgency.service.openai;

import com.example.urgency.service.UrgencyInferenceEngine;

import java.util.Objects;

public final class OpenAiUrgencyInferenceEngine implements UrgencyInferenceEngine {

    private static final String SCORER_PROVIDER_LABEL = "OpenAI urgency scorer provider";

    private final OpenAiUrgencyScorerProvider scorerProvider;

    public OpenAiUrgencyInferenceEngine(OpenAiUrgencyScorerProvider scorerProvider) {
        this.scorerProvider = Objects.requireNonNull(scorerProvider, SCORER_PROVIDER_LABEL);
        this.scorerProvider.scorer();
    }

    @Override
    public double score(String complaint) {
        return scorerProvider.scorer().score(complaint);
    }
}
