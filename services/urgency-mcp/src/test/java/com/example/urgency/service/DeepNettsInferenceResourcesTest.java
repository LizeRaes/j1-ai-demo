package com.example.urgency.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.urgency.embedding.EmbeddingGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeepNettsInferenceResourcesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesMissingModelFile() {
        DeepNettsInferenceResources resources = new DeepNettsInferenceResources(settings("missing.dnet"), StubEmbeddingGenerator::new);

        IllegalStateException exception = assertThrows(IllegalStateException.class, resources::validateStartupConfiguration);

        assertEquals(
                "Scorer model file does not exist: " + temporaryDirectory.resolve("missing.dnet"),
                exception.getMessage()
        );
    }

    @Test
    void validatesExistingModelFile() throws IOException {
        Files.createFile(temporaryDirectory.resolve("model.dnet"));
        DeepNettsInferenceResources resources = new DeepNettsInferenceResources(settings("model.dnet"), StubEmbeddingGenerator::new);

        resources.validateStartupConfiguration();
    }

    @Test
    void cachesEmbeddingGenerator() {
        AtomicInteger creations = new AtomicInteger();
        StubEmbeddingGenerator generator = new StubEmbeddingGenerator();
        DeepNettsInferenceResources resources = new DeepNettsInferenceResources(settings("model.dnet"), () -> {
            creations.incrementAndGet();
            return generator;
        });

        assertSame(generator, resources.embeddingGenerator());
        assertSame(generator, resources.embeddingGenerator());
        assertEquals(1, creations.get());
    }

    private ScorerModelSettings settings(String modelName) {
        return ScorerModelSettings.of(modelName, temporaryDirectory);
    }

    private static final class StubEmbeddingGenerator implements EmbeddingGenerator {
        @Override
        public float[] embed(String text) {
            return new float[] {1.0f};
        }
    }
}
