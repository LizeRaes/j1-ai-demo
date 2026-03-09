package com.example.urgency.embedding;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates 384-dim embeddings using DJL + sentence-transformers/all-MiniLM-L6-v2.
 * Model is pulled from Hugging Face via djl:// URL on first use.
 */
public class DJLEmbeddingGenerator implements AutoCloseable {

    private static final String MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2";
    private static final int EMBEDDING_DIM = 384;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    public DJLEmbeddingGenerator() {
        // Lazy init on first embed() call
    }

    private void ensureLoaded() {
        if (predictor != null) return;
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

    /**
     * Embed a single text. Returns 384-dim float array (MiniLM).
     */
    public float[] embed(String text) {
        ensureLoaded();
        try {
            return predictor.predict(text);
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed for: " + text, e);
        }
    }

    /**
     * Embed multiple texts. Placeholder: sequential for now; batching can be added later.
     */
    public List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).collect(Collectors.toList());
    }

    public static int embeddingDimension() {
        return EMBEDDING_DIM;
    }

    @Override
    public void close() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
    }
}
