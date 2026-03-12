package com.example.urgency.embedding;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;

import java.io.IOException;

/**
 * Generates 384-dim embeddings using DJL + sentence-transformers/all-MiniLM-L6-v2.
 */
public class DJLEmbeddingGenerator implements EmbeddingGenerator, AutoCloseable {

    private static final String MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2";

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    private void ensureLoaded() {
        if (predictor != null) {
            return;
        }
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/" + MODEL_ID)
                    .optEngine("PyTorch")
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .build();

            model = criteria.loadModel();
            predictor = model.newPredictor();
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            throw new RuntimeException("Failed to load embedding model: " + MODEL_ID, e);
        }
    }

    @Override
    public float[] embed(String text) {
        ensureLoaded();
        try {
            return predictor.predict(text);
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed", e);
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
}
