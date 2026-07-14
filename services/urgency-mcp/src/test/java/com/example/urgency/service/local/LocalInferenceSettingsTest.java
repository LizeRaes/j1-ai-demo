package com.example.urgency.service.local;

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
                "feature-hash",
                Path.of("embeddings"),
                4);

        assertEquals(modelDirectory.toAbsolutePath().normalize().resolve("scorer.dnet"), settings.scorerModelPath());
    }

    @Test
    void validatesRequiredValues() {
        Path path = Path.of("models");

        assertThrows(IllegalArgumentException.class, () -> new LocalInferenceSettings(" ", path, "feature-hash", path, 4));
        assertThrows(NullPointerException.class, () -> new LocalInferenceSettings("scorer.dnet", null, "feature-hash", path, 4));
        assertThrows(IllegalArgumentException.class, () -> new LocalInferenceSettings("scorer.dnet", path, " ", path, 4));
        assertThrows(NullPointerException.class, () -> new LocalInferenceSettings("scorer.dnet", path, "feature-hash", null, 4));
        assertThrows(IllegalArgumentException.class, () -> new LocalInferenceSettings("scorer.dnet", path, "feature-hash", path, 0));
    }
}
