package com.example.urgency.service;

import java.nio.file.Path;
import java.util.List;

import com.example.urgency.config.RuntimeConfig;
import io.helidon.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class UrgencyInferenceConfigurationTest {

    private static final List<String> CONFIG_KEYS = List.of(
            "urgency.provider",
            "urgency.providers.local.model.name",
            "urgency.providers.local.model.location",
            "urgency.providers.local.embedding.name",
            "urgency.providers.local.embedding.location",
            "urgency.providers.local.embedding.dimensions"
    );

    @AfterEach
    void clearConfiguration() {
        CONFIG_KEYS.forEach(System::clearProperty);
    }

    @Test
    void readsLocalConfiguration() {
        System.setProperty("urgency.provider", "local");
        System.setProperty("urgency.providers.local.model.name", "model.dnet");
        System.setProperty("urgency.providers.local.model.location", "models");
        System.setProperty("urgency.providers.local.embedding.name", "feature-hash");
        System.setProperty("urgency.providers.local.embedding.location", "embeddings");
        System.setProperty("urgency.providers.local.embedding.dimensions", "4");

        UrgencyInferenceConfiguration configuration = UrgencyInferenceConfiguration.from(new RuntimeConfig(Config.create()));

        LocalUrgencyInferenceConfiguration local = assertInstanceOf(LocalUrgencyInferenceConfiguration.class, configuration);
        assertEquals(UrgencyProvider.LOCAL, local.provider());
        assertEquals(Path.of("models").toAbsolutePath().normalize().resolve("model.dnet"), local.settings().scorerModelPath());
    }

    @Test
    void openAiConfigurationDoesNotReadLocalSettings() {
        System.setProperty("urgency.provider", "openai");

        UrgencyInferenceConfiguration configuration = UrgencyInferenceConfiguration.from(new RuntimeConfig(Config.create()));

        assertInstanceOf(OpenAiUrgencyInferenceConfiguration.class, configuration);
        assertEquals(UrgencyProvider.OPENAI, configuration.provider());
    }
}
