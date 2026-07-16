package com.example.urgency.service;

import java.util.Objects;

import com.example.urgency.embedding.EmbeddingGenerator;
import deepnetts.net.FeedForwardNetwork;

final class DeepNettsUrgencyInferenceEngine implements UrgencyInferenceEngine {

    private static final String RESOURCES_LABEL = "DeepNetts inference resources";

    private final DeepNettsInferenceResources resources;

    DeepNettsUrgencyInferenceEngine(DeepNettsInferenceResources resources) {
        this.resources = Objects.requireNonNull(resources, RESOURCES_LABEL);
        this.resources.validateStartupConfiguration();
    }

    @Override
    public double score(String complaint) {
        FeedForwardNetwork net = resources.scorerNet();
        EmbeddingGenerator generator = resources.embeddingGenerator();
        float[] vector = generator.embed(complaint);
        float score01 = net.predict(vector)[0];
        double score10 = Math.round(score01 * 20.0) / 2.0;
        return Math.max(0.0, Math.min(10.0, score10));
    }
}
