package com.example.document.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class EmbeddingService {

    // EmbeddingModel is automatically provided by quarkus-langchain4j-openai extension
    @Inject
    EmbeddingModel embeddingModel;

    public float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(text).content();
        List<Float> vectorList = embedding.vectorAsList();
        float[] result = new float[vectorList.size()];
        for (int i = 0; i < vectorList.size(); i++) {
            result[i] = vectorList.get(i);
        }
        return result;
    }
}
