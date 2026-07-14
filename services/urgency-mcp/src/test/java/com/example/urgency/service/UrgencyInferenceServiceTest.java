package com.example.urgency.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrgencyInferenceServiceTest {

    private static final List<String> CONFIG_KEYS = List.of(
            "urgency.provider",
            "urgency.providers.local.model.name",
            "urgency.providers.local.model.location",
            "urgency.providers.local.embedding.name",
            "urgency.providers.local.embedding.location",
            "urgency.providers.local.embedding.dimensions",
            "urgency.providers.openai.model.name",
            "urgency.providers.openai.prompt",
            "urgency.providers.openai.temperature",
            "urgency.providers.openai.max-completion-tokens",
            "openai.api-key"
    );

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void clearConfiguration() {
        clearSystemProperties();
    }

    @AfterEach
    void resetConfiguration() {
        clearSystemProperties();
    }

    @Test
    void rejectsUnsupportedProvider() {
        System.setProperty("urgency.provider", "remote");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                UrgencyInferenceService::new
        );

        assertEquals("Unsupported urgency.provider: remote", exception.getMessage());
    }

    @Test
    void rejectsMissingScorerModel() {
        System.setProperty("urgency.provider", "local");
        System.setProperty("urgency.providers.local.model.name", "missing.dnet");
        System.setProperty("urgency.providers.local.model.location", temporaryDirectory.toString());
        setLocalEmbeddingConfiguration();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                UrgencyInferenceService::new
        );

        assertEquals(
                "Scorer model file does not exist: " + temporaryDirectory.resolve("missing.dnet"),
                exception.getMessage()
        );
    }

    @Test
    void openAiProviderDoesNotRequireLocalScorerModel() {
        System.setProperty("urgency.provider", "openai");
        System.setProperty("openai.api-key", "test-key");
        System.setProperty("urgency.providers.openai.model.name", "gpt-4.1-mini");
        System.setProperty("urgency.providers.openai.prompt", "Return a numeric urgency score.");
        System.setProperty("urgency.providers.openai.temperature", "0.0");
        System.setProperty("urgency.providers.openai.max-completion-tokens", "20");
        System.setProperty("urgency.providers.local.model.name", "missing.dnet");
        System.setProperty("urgency.providers.local.model.location", temporaryDirectory.toString());

        new UrgencyInferenceService();
    }

    @Test
    void rejectsNullComplaint() throws IOException {
        UrgencyInferenceService service = serviceWithExistingModelFile();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.score(null)
        );

        assertEquals("complaint is required", exception.getMessage());
    }

    @Test
    void rejectsBlankComplaint() throws IOException {
        UrgencyInferenceService service = serviceWithExistingModelFile();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.score("  ")
        );

        assertEquals("complaint is required", exception.getMessage());
    }

    private UrgencyInferenceService serviceWithExistingModelFile() throws IOException {
        Path model = temporaryDirectory.resolve("model.dnet");
        Files.createFile(model);
        System.setProperty("urgency.provider", "local");
        System.setProperty("urgency.providers.local.model.name", model.getFileName().toString());
        System.setProperty("urgency.providers.local.model.location", temporaryDirectory.toString());
        setLocalEmbeddingConfiguration();
        return new UrgencyInferenceService();
    }

    private static void setLocalEmbeddingConfiguration() {
        System.setProperty("urgency.providers.local.embedding.name", "feature-hash");
        System.setProperty("urgency.providers.local.embedding.location", "embeddings");
        System.setProperty("urgency.providers.local.embedding.dimensions", "4");
    }

    private static void clearSystemProperties() {
        CONFIG_KEYS.forEach(System::clearProperty);
    }
}
