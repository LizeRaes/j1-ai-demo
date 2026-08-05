package com.example.urgency.service;

import com.example.urgency.embedding.LocalEmbeddingGenerator;
import com.example.urgency.validation.RequiredText;

import java.nio.file.Path;
import java.util.Objects;

final class LocalInferenceSettings {

    private static final String SCORER_SETTINGS_LABEL = "scorer model settings";
    private static final String EMBEDDING_NAME_LABEL = "local embedding model name";
    private static final String EMBEDDING_LOCATION_LABEL = "local embedding model location";
    private static final String INVALID_DIMENSIONS_MESSAGE = "local embedding dimensions must be positive";

    private final ScorerModelSettings scorerSettings;
    private final String embeddingModelName;
    private final Path embeddingModelLocation;
    private final int embeddingDimensions;

    LocalInferenceSettings(String modelName, Path modelLocation,
                           String embeddingModelName, Path embeddingModelLocation,
                           int embeddingDimensions) {
        this(ScorerModelSettings.of(modelName, modelLocation),
                embeddingModelName,
                embeddingModelLocation,
                embeddingDimensions);
    }

    LocalInferenceSettings(ScorerModelSettings scorerSettings,
                           String embeddingModelName, Path embeddingModelLocation,
                           int embeddingDimensions) {
        this.scorerSettings = Objects.requireNonNull(scorerSettings, SCORER_SETTINGS_LABEL);
        this.embeddingModelName = new RequiredText(EMBEDDING_NAME_LABEL).require(embeddingModelName);
        this.embeddingModelLocation = Objects.requireNonNull(embeddingModelLocation, EMBEDDING_LOCATION_LABEL)
                .toAbsolutePath()
                .normalize();
        if (embeddingDimensions < 1) {
            throw new IllegalArgumentException(INVALID_DIMENSIONS_MESSAGE);
        }
        this.embeddingDimensions = embeddingDimensions;
    }

    ScorerModelSettings scorerSettings() {
        return scorerSettings;
    }

    Path scorerModelPath() {
        return scorerSettings.scorerModelPath();
    }

    LocalEmbeddingGenerator embeddingGenerator() {
        return new LocalEmbeddingGenerator(embeddingModelName, embeddingModelLocation, embeddingDimensions);
    }
}
