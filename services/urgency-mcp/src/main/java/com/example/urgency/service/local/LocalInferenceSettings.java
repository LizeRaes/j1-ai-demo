package com.example.urgency.service.local;

import java.nio.file.Path;
import java.util.Objects;

import com.example.urgency.embedding.LocalEmbeddingGenerator;
import com.example.urgency.validation.RequiredText;

public record LocalInferenceSettings(String modelName, Path modelLocation,
                                     String embeddingModelName, Path embeddingModelLocation,
                                     int embeddingDimensions) {

    private static final String MODEL_NAME_LABEL = "local scorer model name";
    private static final String MODEL_LOCATION_LABEL = "local scorer model location";
    private static final String EMBEDDING_NAME_LABEL = "local embedding model name";
    private static final String EMBEDDING_LOCATION_LABEL = "local embedding model location";
    private static final String INVALID_DIMENSIONS_MESSAGE = "local embedding dimensions must be positive";

    public LocalInferenceSettings {
        modelName = new RequiredText(MODEL_NAME_LABEL).require(modelName);
        modelLocation = Objects.requireNonNull(modelLocation, MODEL_LOCATION_LABEL).toAbsolutePath().normalize();
        embeddingModelName = new RequiredText(EMBEDDING_NAME_LABEL).require(embeddingModelName);
        embeddingModelLocation = Objects.requireNonNull(embeddingModelLocation, EMBEDDING_LOCATION_LABEL)
                .toAbsolutePath()
                .normalize();
        if (embeddingDimensions < 1) {
            throw new IllegalArgumentException(INVALID_DIMENSIONS_MESSAGE);
        }
    }

    public Path scorerModelPath() {
        return modelLocation.resolve(modelName).normalize();
    }

    LocalEmbeddingGenerator embeddingGenerator() {
        return new LocalEmbeddingGenerator(embeddingModelName, embeddingModelLocation, embeddingDimensions);
    }
}
