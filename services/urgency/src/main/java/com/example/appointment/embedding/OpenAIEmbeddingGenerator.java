package com.example.appointment.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * OpenAI embeddings via LangChain4j. Uses OPENAI_API_KEY env var.
 */
@ApplicationScoped
public class OpenAIEmbeddingGenerator implements EmbeddingGenerator {

    private final Instance<EmbeddingModel> embeddingModelInstance;
    private volatile EmbeddingModel fallbackModel;

    @Inject
    public OpenAIEmbeddingGenerator(Instance<EmbeddingModel> embeddingModelInstance) {
        this.embeddingModelInstance = embeddingModelInstance;
    }

    @Override
    public float[] embed(String text) {
        EmbeddingModel model = resolveModel();
        Embedding embedding = model.embed(text).content();
        return embedding.vector();
    }

    public void validateConfiguration() {
        resolveModel();
    }

    private EmbeddingModel resolveModel() {
        if (!embeddingModelInstance.isUnsatisfied()) {
            return embeddingModelInstance.get();
        }

        EmbeddingModel cached = fallbackModel;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (fallbackModel == null) {
                String apiKey = resolveApiKey();
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "OpenAI embedding model is not configured. Provide key via 'quarkus.langchain4j.openai.api-key' " +
                                    "or OPENAI_API_KEY."
                    );
                }
                fallbackModel = OpenAiEmbeddingModel.builder()
                        .apiKey(apiKey)
                        .modelName(OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL)
                        .build();
            }
            return fallbackModel;
        }
    }

    private String resolveApiKey() {
        String fromSystemProperty = System.getProperty("quarkus.langchain4j.openai.api-key");
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return fromSystemProperty;
        }

        String fromConfig = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.langchain4j.openai.api-key", String.class)
                .orElse("");
        if (!fromConfig.isBlank()) {
            return fromConfig;
        }

        return System.getenv("OPENAI_API_KEY");
    }
}
