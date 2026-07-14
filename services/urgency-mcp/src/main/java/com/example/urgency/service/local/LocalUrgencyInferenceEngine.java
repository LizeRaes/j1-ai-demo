package com.example.urgency.service.local;

import java.util.Objects;

import com.example.urgency.embedding.EmbeddingGenerator;
import com.example.urgency.service.UrgencyInferenceEngine;
import deepnetts.net.FeedForwardNetwork;

public final class LocalUrgencyInferenceEngine implements UrgencyInferenceEngine {

    private static final String RESOURCES_LABEL = "local inference resources";

    private final LocalInferenceResources resources;

    public LocalUrgencyInferenceEngine(LocalInferenceResources resources) {
        this.resources = Objects.requireNonNull(resources, RESOURCES_LABEL);
        this.resources.validateStartupConfiguration();
    }

    @Override
    public double score(String complaint) {
        FeedForwardNetwork net = resources.scorerNet();
        EmbeddingGenerator generator = resources.embeddingGenerator();
        float[] vec = generator.embed(complaint);
        float score01 = net.predict(vec)[0];
        double score10 = Math.round(score01 * 20.0) / 2.0;
        return Math.max(0.0, Math.min(10.0, score10));
    }
}
