package com.example.urgency.embedding;

import java.nio.file.Path;
import java.util.Locale;

import com.example.urgency.validation.RequiredText;
import java.util.Objects;

public final class LocalEmbeddingGenerator implements EmbeddingGenerator {

    private static final String MODEL_NAME_LABEL = "local embedding model name";
    private static final String MODEL_LOCATION_LABEL = "local embedding model location";
    private static final String INVALID_DIMENSIONS_MESSAGE = "local embedding dimensions must be positive";
    private static final String TEXT_LABEL = "text";
    private static final String TOKEN_SPLIT_PATTERN = "[^\\p{Alnum}]+";

    private final String modelName;
    private final Path modelLocation;
    private final int dimensions;
    private final int seed;

    public LocalEmbeddingGenerator(String modelName, Path modelLocation, int dimensions) {
        this.modelName = new RequiredText(MODEL_NAME_LABEL).require(modelName);
        this.modelLocation = Objects.requireNonNull(modelLocation, MODEL_LOCATION_LABEL);
        if (dimensions < 1) {
            throw new IllegalArgumentException(INVALID_DIMENSIONS_MESSAGE);
        }
        this.dimensions = dimensions;
        this.seed = Objects.hash(this.modelName, this.modelLocation.toAbsolutePath().normalize());
    }

    @Override
    public float[] embed(String text) {
        Objects.requireNonNull(text, TEXT_LABEL);
        float[] vector = new float[dimensions];
        String[] tokens = text.toLowerCase(Locale.ROOT).split(TOKEN_SPLIT_PATTERN);
        for (String token : tokens) {
            if (!token.isBlank()) {
                addToken(vector, token);
            }
        }
        normalize(vector);
        return vector;
    }

    private void addToken(float[] vector, String token) {
        int hash = Objects.hash(seed, token);
        int index = Math.floorMod(hash, dimensions);
        vector[index] += (hash & 1) == 0 ? 1.0f : -1.0f;
    }

    private static void normalize(float[] vector) {
        double magnitude = 0.0;
        for (float value : vector) {
            magnitude += value * value;
        }
        if (magnitude == 0.0) {
            return;
        }
        float scale = (float) (1.0 / Math.sqrt(magnitude));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }
}
