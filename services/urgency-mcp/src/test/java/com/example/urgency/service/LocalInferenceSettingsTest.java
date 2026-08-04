package com.example.urgency.service;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalInferenceSettingsTest {

    @Test
    void resolvesScorerModelPath() {
        Path modelDirectory = Path.of("models");
        LocalInferenceSettings settings = new LocalInferenceSettings(
                "scorer.dnet",
                modelDirectory,
                "sentence-transformers/all-MiniLM-L6-v2",
                Path.of("embeddings"),
                384);

        assertEquals(modelDirectory.toAbsolutePath().normalize().resolve("scorer.dnet"), settings.scorerModelPath());
    }

    @Test
    void validatesRequiredValues() {
        Path path = Path.of("models");

        assertThrows(IllegalArgumentException.class, () -> new LocalInferenceSettings(" ", path, "sentence-transformers/all-MiniLM-L6-v2", path, 384));
        assertThrows(NullPointerException.class, () -> new LocalInferenceSettings("scorer.dnet", null, "sentence-transformers/all-MiniLM-L6-v2", path, 384));
        assertThrows(IllegalArgumentException.class, () -> new LocalInferenceSettings("scorer.dnet", path, " ", path, 384));
        assertThrows(NullPointerException.class, () -> new LocalInferenceSettings("scorer.dnet", path, "sentence-transformers/all-MiniLM-L6-v2", null, 384));
        assertThrows(IllegalArgumentException.class, () -> new LocalInferenceSettings("scorer.dnet", path, "sentence-transformers/all-MiniLM-L6-v2", path, 0));
    }
}
