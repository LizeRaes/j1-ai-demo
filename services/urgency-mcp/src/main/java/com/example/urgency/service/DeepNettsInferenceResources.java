package com.example.urgency.service;

import com.example.urgency.embedding.EmbeddingGenerator;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.util.FileIO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

final class DeepNettsInferenceResources {

    private static final String SETTINGS_LABEL = "scorer model settings";
    private static final String EMBEDDING_GENERATOR_SUPPLIER_LABEL = "embedding generator supplier";
    private static final String MISSING_SCORER_MODEL_PREFIX = "Scorer model file does not exist: ";
    private static final String SCORER_LOAD_FAILURE_PREFIX = "Failed to load scorer model from ";

    private final ScorerModelSettings settings;
    private final Supplier<EmbeddingGenerator> embeddingGeneratorSupplier;
    private volatile FeedForwardNetwork scorerNet;
    private volatile EmbeddingGenerator embeddingGenerator;

    DeepNettsInferenceResources(ScorerModelSettings settings, Supplier<EmbeddingGenerator> embeddingGeneratorSupplier) {
        this.settings = Objects.requireNonNull(settings, SETTINGS_LABEL);
        this.embeddingGeneratorSupplier = Objects.requireNonNull(
                embeddingGeneratorSupplier,
                EMBEDDING_GENERATOR_SUPPLIER_LABEL);
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
                embeddingGenerator = embeddingGeneratorSupplier.get();
            }
            return embeddingGenerator;
        }
    }

    private static FeedForwardNetwork loadScorerNet(Path modelPath) {
        try {
            return FileIO.createFromFile(modelPath.toString(), FeedForwardNetwork.class);
        } catch (Exception e) {
            throw new RuntimeException(SCORER_LOAD_FAILURE_PREFIX + modelPath, e);
        }
    }
}
