package com.example.urgency.embedding;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalEmbeddingGeneratorTest {

    @Test
    void rejectsInvalidConstructorArguments() {
        Path location = Path.of("embeddings");

        assertThrows(IllegalArgumentException.class, () -> new LocalEmbeddingGenerator(" ", location, 384));
        assertThrows(NullPointerException.class, () -> new LocalEmbeddingGenerator("sentence-transformers/all-MiniLM-L6-v2", null, 384));
        assertThrows(IllegalArgumentException.class, () -> new LocalEmbeddingGenerator("sentence-transformers/all-MiniLM-L6-v2", location, 0));
    }

    @Test
    void requiresTextToEmbed() {
        LocalEmbeddingGenerator generator = new LocalEmbeddingGenerator(
                "sentence-transformers/all-MiniLM-L6-v2",
                Path.of("embeddings"),
                384);

        assertThrows(IllegalArgumentException.class, () -> generator.embed(null));
        assertThrows(IllegalArgumentException.class, () -> generator.embed("  "));
    }
}
