package com.example.urgency.service;

import java.nio.file.Path;

import com.example.urgency.config.RuntimeConfig;
import com.example.urgency.service.local.LocalInferenceSettings;

sealed interface UrgencyInferenceConfiguration permits LocalUrgencyInferenceConfiguration, OpenAiUrgencyInferenceConfiguration {

    String PROVIDER_KEY = "urgency.provider";
    String PROVIDER_LABEL = "provider";
    String LOCAL_MODEL_NAME_KEY = "urgency.providers.local.model.name";
    String LOCAL_MODEL_LOCATION_KEY = "urgency.providers.local.model.location";
    String LOCAL_EMBEDDING_NAME_KEY = "urgency.providers.local.embedding.name";
    String LOCAL_EMBEDDING_LOCATION_KEY = "urgency.providers.local.embedding.location";
    String LOCAL_EMBEDDING_DIMENSIONS_KEY = "urgency.providers.local.embedding.dimensions";

    UrgencyProvider provider();

    static UrgencyInferenceConfiguration from(RuntimeConfig config) {
        UrgencyProvider provider = UrgencyProvider.parse(config.requiredText(PROVIDER_KEY, PROVIDER_LABEL));
        return switch (provider) {
            case LOCAL -> new LocalUrgencyInferenceConfiguration(localSettings(config));
            case OPENAI -> new OpenAiUrgencyInferenceConfiguration();
        };
    }

    private static LocalInferenceSettings localSettings(RuntimeConfig config) {
        return new LocalInferenceSettings(
                config.requiredText(LOCAL_MODEL_NAME_KEY, LOCAL_MODEL_NAME_KEY),
                Path.of(config.requiredText(LOCAL_MODEL_LOCATION_KEY, LOCAL_MODEL_LOCATION_KEY)),
                config.requiredText(LOCAL_EMBEDDING_NAME_KEY, LOCAL_EMBEDDING_NAME_KEY),
                Path.of(config.requiredText(LOCAL_EMBEDDING_LOCATION_KEY, LOCAL_EMBEDDING_LOCATION_KEY)),
                config.integer(LOCAL_EMBEDDING_DIMENSIONS_KEY));
    }
}

record LocalUrgencyInferenceConfiguration(LocalInferenceSettings settings) implements UrgencyInferenceConfiguration {
    @Override
    public UrgencyProvider provider() {
        return UrgencyProvider.LOCAL;
    }
}

record OpenAiUrgencyInferenceConfiguration() implements UrgencyInferenceConfiguration {
    @Override
    public UrgencyProvider provider() {
        return UrgencyProvider.OPENAI;
    }
}
