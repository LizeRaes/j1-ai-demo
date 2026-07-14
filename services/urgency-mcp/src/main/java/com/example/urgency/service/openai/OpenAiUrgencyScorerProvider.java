package com.example.urgency.service.openai;

import java.util.Objects;

import com.example.urgency.service.UrgencyScorer;
import io.helidon.config.Config;

public final class OpenAiUrgencyScorerProvider {

    private static final String CONFIG_LABEL = "config";

    private final Config config;
    private volatile UrgencyScorer scorer;

    public OpenAiUrgencyScorerProvider(Config config) {
        this.config = Objects.requireNonNull(config, CONFIG_LABEL);
    }

    UrgencyScorer scorer() {
        UrgencyScorer cached = scorer;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (scorer == null) {
                scorer = new OpenAIUrgencyScorer(config);
            }
            return scorer;
        }
    }
}
