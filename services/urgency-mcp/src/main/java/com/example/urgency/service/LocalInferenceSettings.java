package com.example.urgency.service;

import java.nio.file.Path;
import java.util.Objects;

import com.example.urgency.embedding.LocalEmbeddingGenerator;
import com.example.urgency.validation.RequiredText;

record LocalInferenceSettings(ScorerModelSettings scorerSettings,
                              String embeddingModelName, Path embeddingModelLocation,
                              int embeddingDimensions) {

    private static final String SCORER_SETTINGS_LABEL = "scorer model settings";
    private static final String EMBEDDING_NAME_LABEL = "local embedding model name";
    private static final String EMBEDDING_LOCATION_LABEL = "local embedding model location";
    private static final String INVALID_DIMENSIONS_MESSAGE = "local embedding dimensions must be positive";

    LocalInferenceSettings(String modelName, Path modelLocation,
                           String embeddingModelName, Path embeddingModelLocation,
                           int embeddingDimensions) {
        this(new ScorerModelSettings(modelName, modelLocation),
                embeddingModelName,
                embeddingModelLocation,
                embeddingDimensions);
    }

    LocalInferenceSettings {
        scorerSettings = Objects.requireNonNull(scorerSettings, SCORER_SETTINGS_LABEL);
        embeddingModelName = new RequiredText(EMBEDDING_NAME_LABEL).require(embeddingModelName);
        embeddingModelLocation = Objects.requireNonNull(embeddingModelLocation, EMBEDDING_LOCATION_LABEL)
                .toAbsolutePath()
                .normalize();
        if (embeddingDimensions < 1) {
            throw new IllegalArgumentException(INVALID_DIMENSIONS_MESSAGE);
        }
    }

    Path scorerModelPath() {
        return scorerSettings.scorerModelPath();
    }

    LocalEmbeddingGenerator embeddingGenerator() {
        return new LocalEmbeddingGenerator(embeddingModelName, embeddingModelLocation, embeddingDimensions);
    }
}
