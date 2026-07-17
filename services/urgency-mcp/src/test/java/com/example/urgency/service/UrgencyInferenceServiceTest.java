package com.example.urgency.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.example.urgency.embedding.OpenAIEmbeddingGenerator;
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
            "urgency.providers.openai.model.location",
            OpenAIEmbeddingGenerator.MODEL_NAME_KEY,
            OpenAIEmbeddingGenerator.DIMENSIONS_KEY,
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
    void rejectsMissingLocalScorerModel() {
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
    void openAiProviderRequiresConfiguredScorerModel() {
        System.setProperty("urgency.provider", "openai");
        System.setProperty("openai.api-key", "test-key");
        System.setProperty("urgency.providers.openai.model.name", "missing.dnet");
        System.setProperty("urgency.providers.openai.model.location", temporaryDirectory.toString());
        System.setProperty(OpenAIEmbeddingGenerator.MODEL_NAME_KEY, "text-embedding-3-small");
        System.setProperty(OpenAIEmbeddingGenerator.DIMENSIONS_KEY, "1536");

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
    void openAiProviderRequiresEmbeddingApiKey() throws IOException {
        Path model = temporaryDirectory.resolve("model-scorer-openai.dnet");
        Files.createFile(model);
        System.setProperty("urgency.provider", "openai");
        System.setProperty("urgency.providers.openai.model.name", model.getFileName().toString());
        System.setProperty("urgency.providers.openai.model.location", temporaryDirectory.toString());
        System.setProperty(OpenAIEmbeddingGenerator.MODEL_NAME_KEY, "text-embedding-3-small");
        System.setProperty(OpenAIEmbeddingGenerator.DIMENSIONS_KEY, "1536");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                UrgencyInferenceService::new
        );

        assertEquals("OpenAI embeddings require API key. Set 'openai.api-key' or OPENAI_API_KEY.", exception.getMessage());
    }

    @Test
    void openAiProviderUsesEmbeddingConfigurationAndScorerModel() throws IOException {
        Path model = temporaryDirectory.resolve("model-scorer-openai.dnet");
        Files.createFile(model);
        System.setProperty("urgency.provider", "openai");
        System.setProperty("openai.api-key", "test-key");
        System.setProperty("urgency.providers.openai.model.name", model.getFileName().toString());
        System.setProperty("urgency.providers.openai.model.location", temporaryDirectory.toString());
        System.setProperty(OpenAIEmbeddingGenerator.MODEL_NAME_KEY, "text-embedding-3-small");
        System.setProperty(OpenAIEmbeddingGenerator.DIMENSIONS_KEY, "1536");

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
        System.setProperty("urgency.providers.local.embedding.name", "sentence-transformers/all-MiniLM-L6-v2");
        System.setProperty("urgency.providers.local.embedding.location", "embeddings");
        System.setProperty("urgency.providers.local.embedding.dimensions", "384");
    }

    private static void clearSystemProperties() {
        CONFIG_KEYS.forEach(System::clearProperty);
    }
}
