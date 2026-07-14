package com.example.urgency.embedding;

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.helidon.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAIEmbeddingGeneratorTest {

    @AfterEach
    void clearConfiguration() {
        System.clearProperty(OpenAIEmbeddingGenerator.API_KEY_PROPERTY);
        System.clearProperty(OpenAIEmbeddingGenerator.MODEL_NAME_KEY);
        System.clearProperty(OpenAIEmbeddingGenerator.DIMENSIONS_KEY);
    }

    @Test
    void delegatesEmbeddingToConfiguredModel() {
        StubEmbeddingModel model = new StubEmbeddingModel(new float[] {0.25f, 0.75f});
        OpenAIEmbeddingGenerator generator = new OpenAIEmbeddingGenerator(model);

        float[] embedding = generator.embed("Patient cannot access medication refill");

        assertArrayEquals(new float[] {0.25f, 0.75f}, embedding);
        assertEquals("Patient cannot access medication refill", model.text);
    }

    @Test
    void resolvesApiKeyFromSystemProperty() {
        System.setProperty(OpenAIEmbeddingGenerator.API_KEY_PROPERTY, "test-key");

        assertEquals("test-key", OpenAIEmbeddingGenerator.resolveApiKey(Config.create()).orElseThrow());
    }

    @Test
    void settingsRequireApiKeyAndPositiveDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OpenAIEmbeddingGenerator.Settings("text-embedding-3-small", java.util.Optional.empty(), " ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OpenAIEmbeddingGenerator.Settings("text-embedding-3-small", java.util.Optional.of(0), "test-key")
        );
    }

    @Test
    void requiresTextToEmbed() {
        OpenAIEmbeddingGenerator generator = new OpenAIEmbeddingGenerator(new StubEmbeddingModel(new float[] {1.0f}));

        assertThrows(IllegalArgumentException.class, () -> generator.embed(" "));
    }

    private static final class StubEmbeddingModel implements EmbeddingModel {
        private final float[] embedding;
        private String text;

        private StubEmbeddingModel(float[] embedding) {
            this.embedding = embedding;
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            text = textSegments.getFirst().text();
            return Response.from(List.of(Embedding.from(embedding)));
        }
    }
}
