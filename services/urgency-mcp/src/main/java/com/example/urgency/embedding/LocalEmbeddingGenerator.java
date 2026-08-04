package com.example.urgency.embedding;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import com.example.urgency.validation.RequiredText;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;

public final class LocalEmbeddingGenerator implements EmbeddingGenerator, AutoCloseable {

    private static final String MODEL_NAME_LABEL = "local embedding model name";
    private static final String MODEL_LOCATION_LABEL = "local embedding model location";
    private static final String INVALID_DIMENSIONS_MESSAGE = "local embedding dimensions must be positive";
    private static final String TEXT_LABEL = "text";
    private static final String DJL_MODEL_URL_PREFIX = "djl://ai.djl.huggingface.pytorch/";
    private static final String PREDICTOR_LABEL = "local embedding predictor";
    public static final String ENGINE = "PyTorch";

    private final String modelName;
    private final Path modelLocation;
    private final int dimensions;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    public LocalEmbeddingGenerator(String modelName, Path modelLocation, int dimensions) {
        this.modelName = new RequiredText(MODEL_NAME_LABEL).require(modelName);
        this.modelLocation = Objects.requireNonNull(modelLocation, MODEL_LOCATION_LABEL).toAbsolutePath().normalize();
        if (dimensions < 1) {
            throw new IllegalArgumentException(INVALID_DIMENSIONS_MESSAGE);
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        String requiredText = new RequiredText(TEXT_LABEL).require(text);
        ensureLoaded();
        try {
            float[] vector = Objects.requireNonNull(predictor, PREDICTOR_LABEL).predict(requiredText);
            if (vector.length != dimensions) {
                throw new IllegalStateException(
                        "Configured local embedding dimensions " + dimensions
                                + " do not match model output dimensions " + vector.length
                                + " for " + modelName);
            }
            return vector;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed for: " + requiredText, e);
        }
    }

    @Override
    public void close() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
    }

    private void ensureLoaded() {
        if (predictor != null) {
            return;
        }
        synchronized (this) {
            if (predictor == null) {
                predictor = loadPredictor();
            }
        }
    }

    private Predictor<String, float[]> loadPredictor() {
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls(DJL_MODEL_URL_PREFIX + modelName)
                    .optEngine(ENGINE)
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .build();
            model = criteria.loadModel();
            return model.newPredictor();
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            throw new RuntimeException(
                    "Failed to load local embedding model: " + modelName + " (configured location: " + modelLocation + ")",
                    e);
        }
    }
}
