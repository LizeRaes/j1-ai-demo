package com.example.urgency.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModelName;
import io.helidon.config.Config;

/**
 * OpenAI embeddings via LangChain4j.
 */
public class OpenAIEmbeddingGenerator implements EmbeddingGenerator {

    private final EmbeddingModel model;

    public OpenAIEmbeddingGenerator() {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI embeddings require API key. Set 'quarkus.langchain4j.openai.api-key' (or 'openai.api-key') or OPENAI_API_KEY."
            );
        }
        this.model = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL)
                .build();
    }

    @Override
    public float[] embed(String text) {
        Embedding embedding = model.embed(text).content();
        return embedding.vector();
    }

    private String resolveApiKey() {
        String fromSystemProperty = System.getProperty("quarkus.langchain4j.openai.api-key");
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return fromSystemProperty;
        }

        String fromLegacyProperty = System.getProperty("openai.api-key");
        if (fromLegacyProperty != null && !fromLegacyProperty.isBlank()) {
            return fromLegacyProperty;
        }

        Config config = Config.create();
        String fromConfig = config.get("quarkus.langchain4j.openai.api-key").asString().orElse("");
        if (!fromConfig.isBlank()) {
            return fromConfig;
        }
        String fromLegacyConfig = config.get("openai.api-key").asString().orElse("");
        if (!fromLegacyConfig.isBlank()) {
            return fromLegacyConfig;
        }

        return System.getenv("OPENAI_API_KEY");
    }
}
