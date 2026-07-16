package com.example.urgency.service;

import java.util.Objects;
import java.util.function.Supplier;

import com.example.urgency.embedding.EmbeddingGenerator;
import com.example.urgency.embedding.OpenAIEmbeddingGenerator;
import io.helidon.config.Config;

final class UrgencyInferenceEngineFactory {

    private static final String CONFIGURATION_LABEL = "urgency inference configuration";
    private static final String CONFIG_LABEL = "config";

    UrgencyInferenceEngine create(UrgencyInferenceConfiguration configuration, Config config) {
        Objects.requireNonNull(configuration, CONFIGURATION_LABEL);
        Objects.requireNonNull(config, CONFIG_LABEL);
        return switch (configuration) {
            case LocalUrgencyInferenceConfiguration local -> deepNettsEngine(
                    local.settings().scorerSettings(),
                    local.settings()::embeddingGenerator);
            case OpenAiUrgencyInferenceConfiguration openAi -> openAiDeepNettsEngine(openAi.scorerSettings(), config);
        };
    }

    private static UrgencyInferenceEngine openAiDeepNettsEngine(ScorerModelSettings settings, Config config) {
        OpenAIEmbeddingGenerator embeddingGenerator = new OpenAIEmbeddingGenerator(config);
        return deepNettsEngine(settings, () -> embeddingGenerator);
    }

    private static UrgencyInferenceEngine deepNettsEngine(
            ScorerModelSettings settings,
            Supplier<EmbeddingGenerator> embeddingGeneratorSupplier) {
        return new DeepNettsUrgencyInferenceEngine(new DeepNettsInferenceResources(settings, embeddingGeneratorSupplier));
    }
}
