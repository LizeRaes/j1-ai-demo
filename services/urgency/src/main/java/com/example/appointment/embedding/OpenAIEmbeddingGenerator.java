package com.example.appointment.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.enterprise.inject.Instance;

/**
 * OpenAI embeddings via LangChain4j. Uses OPENAI_API_KEY env var.
 */
@ApplicationScoped
public class OpenAIEmbeddingGenerator implements EmbeddingGenerator {

    private final Instance<EmbeddingModel> embeddingModelInstance;

    @Inject
    public OpenAIEmbeddingGenerator(Instance<EmbeddingModel> embeddingModelInstance) {
        this.embeddingModelInstance = embeddingModelInstance;
    }

    @Override
    public float[] embed(String text) {
        if (embeddingModelInstance.isUnsatisfied()) {
            throw new IllegalStateException("OpenAI embedding model is not configured. Set OPENAI_API_KEY and LangChain4j OpenAI settings.");
        }
        EmbeddingModel model = embeddingModelInstance.get();
        Embedding embedding = model.embed(text).content();
        return embedding.vector();
    }
}
