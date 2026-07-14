package com.example.urgency.service;

import java.util.Objects;

import com.example.urgency.service.local.LocalInferenceResources;
import com.example.urgency.service.local.LocalUrgencyInferenceEngine;
import com.example.urgency.service.openai.OpenAiUrgencyInferenceEngine;
import com.example.urgency.service.openai.OpenAiUrgencyScorerProvider;
import io.helidon.config.Config;

final class UrgencyInferenceEngineFactory {

    private static final String CONFIGURATION_LABEL = "urgency inference configuration";
    private static final String CONFIG_LABEL = "config";

    UrgencyInferenceEngine create(UrgencyInferenceConfiguration configuration, Config config) {
        Objects.requireNonNull(configuration, CONFIGURATION_LABEL);
        Objects.requireNonNull(config, CONFIG_LABEL);
        return switch (configuration) {
            case LocalUrgencyInferenceConfiguration local ->
                    new LocalUrgencyInferenceEngine(new LocalInferenceResources(local.settings()));
            case OpenAiUrgencyInferenceConfiguration _ ->
                    new OpenAiUrgencyInferenceEngine(new OpenAiUrgencyScorerProvider(config));
        };
    }
}
