package com.example.urgency.embedding;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalEmbeddingGeneratorTest {

    @Test
    void generatesDeterministicCaseInsensitiveEmbedding() {
        LocalEmbeddingGenerator generator = new LocalEmbeddingGenerator("feature-hash", Path.of("embeddings"), 16);

        float[] first = generator.embed("Billing Portal Broken");
        float[] second = generator.embed("billing portal broken");

        assertArrayEquals(first, second);
        assertEquals(1.0, magnitude(first), 0.000001);
    }

    @Test
    void returnsZeroVectorWhenTextHasNoTokens() {
        LocalEmbeddingGenerator generator = new LocalEmbeddingGenerator("feature-hash", Path.of("embeddings"), 4);

        assertArrayEquals(new float[] {0.0f, 0.0f, 0.0f, 0.0f}, generator.embed("---"));
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        Path location = Path.of("embeddings");

        assertThrows(IllegalArgumentException.class, () -> new LocalEmbeddingGenerator(" ", location, 4));
        assertThrows(NullPointerException.class, () -> new LocalEmbeddingGenerator("feature-hash", null, 4));
        assertThrows(IllegalArgumentException.class, () -> new LocalEmbeddingGenerator("feature-hash", location, 0));
    }

    @Test
    void requiresTextToEmbed() {
        LocalEmbeddingGenerator generator = new LocalEmbeddingGenerator("feature-hash", Path.of("embeddings"), 4);

        assertThrows(NullPointerException.class, () -> generator.embed(null));
    }

    private static double magnitude(float[] vector) {
        double total = 0.0;
        for (float value : vector) {
            total += value * value;
        }
        return Math.sqrt(total);
    }
}
