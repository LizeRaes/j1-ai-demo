package com.example.urgency.service.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalInferenceResourcesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesMissingModelFile() {
        LocalInferenceResources resources = new LocalInferenceResources(settings("missing.dnet"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, resources::validateStartupConfiguration);

        assertEquals(
                "Scorer model file does not exist: " + temporaryDirectory.resolve("missing.dnet"),
                exception.getMessage()
        );
    }

    @Test
    void validatesExistingModelFile() throws IOException {
        Files.createFile(temporaryDirectory.resolve("model.dnet"));
        LocalInferenceResources resources = new LocalInferenceResources(settings("model.dnet"));

        resources.validateStartupConfiguration();
    }

    @Test
    void cachesEmbeddingGenerator() {
        LocalInferenceResources resources = new LocalInferenceResources(settings("model.dnet"));

        assertNotNull(resources.embeddingGenerator());
        assertSame(resources.embeddingGenerator(), resources.embeddingGenerator());
    }

    private LocalInferenceSettings settings(String modelName) {
        return new LocalInferenceSettings(
                modelName,
                temporaryDirectory,
                "feature-hash",
                temporaryDirectory.resolve("embeddings"),
                4);
    }
}
