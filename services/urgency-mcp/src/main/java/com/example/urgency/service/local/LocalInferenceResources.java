package com.example.urgency.service.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.example.urgency.embedding.EmbeddingGenerator;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;

public final class LocalInferenceResources {

    private static final String SETTINGS_LABEL = "local inference settings";
    private static final String MISSING_SCORER_MODEL_PREFIX = "Scorer model file does not exist: ";
    private static final String SCORER_LOAD_FAILURE_PREFIX = "Failed to load scorer model from ";

    private final LocalInferenceSettings settings;
    private volatile FeedForwardNetwork scorerNet;
    private volatile EmbeddingGenerator embeddingGenerator;

    public LocalInferenceResources(LocalInferenceSettings settings) {
        this.settings = Objects.requireNonNull(settings, SETTINGS_LABEL);
    }

    void validateStartupConfiguration() {
        Path modelPath = settings.scorerModelPath();
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalStateException(MISSING_SCORER_MODEL_PREFIX + modelPath);
        }
    }

    FeedForwardNetwork scorerNet() {
        FeedForwardNetwork cached = scorerNet;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (scorerNet == null) {
                scorerNet = loadScorerNet(settings.scorerModelPath());
            }
            return scorerNet;
        }
    }

    EmbeddingGenerator embeddingGenerator() {
        EmbeddingGenerator cached = embeddingGenerator;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (embeddingGenerator == null) {
                embeddingGenerator = settings.embeddingGenerator();
            }
            return embeddingGenerator;
        }
    }

    private static FeedForwardNetwork loadScorerNet(Path modelPath) {
        try {
            return (FeedForwardNetwork) FileIO.createFromFile(modelPath.toString(), FeedForwardNetwork.class);
        } catch (Exception e) {
            throw new RuntimeException(SCORER_LOAD_FAILURE_PREFIX + modelPath, e);
        }
    }
}
