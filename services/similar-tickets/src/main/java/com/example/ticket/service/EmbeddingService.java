package com.example.ticket.service;

import io.helidon.service.registry.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

@Service.Singleton
public class EmbeddingService {

	private final EmbeddingModel model;

    @Service.Inject
	public EmbeddingService(EmbeddingModel model) {
		this.model = model;
	}

	public float[] embed(String text) {
		Embedding embedding = model.embed(text).content();
		return embedding.vector();
	}
}
