package org.example.similarity.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

public class EmbeddingService {

	private final EmbeddingModel model;

	public EmbeddingService(EmbeddingModel model) {
		this.model = model;
	}

	public float[] embed(String text) {
		Embedding embedding = model.embed(text).content();
		return embedding.vector();
	}
}
