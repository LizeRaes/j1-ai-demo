package com.example.urgency.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModelName;

/**
 * OpenAI embeddings via LangChain4j. Uses OPENAI_API_KEY env var.
 */
public class OpenAIEmbeddingGenerator implements EmbeddingGenerator {

    private final EmbeddingModel model;

    public OpenAIEmbeddingGenerator() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required for OpenAI embeddings");
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
}
