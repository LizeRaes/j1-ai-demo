package org.example.similarity;

import io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig;
import io.helidon.service.registry.Services;
import org.example.similarity.service.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.http.media.jackson.JacksonSupport;

import javax.sql.DataSource;

public class Main {

	public static void main(String[] args) {
		Config config = Services.get(Config.class);

		int port = config.get("server.port").asInt().orElse(8082);

		WebServer.builder()
				.config(config.get("server"))
				.routing(Main::routing)
				.mediaContext(it -> it
						.mediaSupportsDiscoverServices(false)
						.addMediaSupport(JacksonSupport.create()).build())
				.build()
				.start();

		System.out.println("Server started at http://localhost:" + port);
	}

	static void routing(HttpRouting.Builder routing) {
		Config config = Services.get(Config.class);
		String configuredApiKey = config.get("openai.api-key").asString().orElse("");
		String apiKey = resolveOpenAiApiKey(configuredApiKey);
		String embeddingModelName = config.get("openai.embedding-model").asString().orElse("text-embedding-3-large");

		EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
				.apiKey(apiKey)
				.modelName(embeddingModelName)
				.build();

		DataSource dataSource = Services.getNamed(DataSource.class, "ora-ucp-ds");

		Config oracleEmbeddingConfig = config.get("langchain4j.oracle.embedding-store");
		OracleEmbeddingStoreConfig storeConfig = OracleEmbeddingStoreConfig.create(oracleEmbeddingConfig);
		EmbeddingStore<TextSegment> embeddingStore = storeConfig.configuredBuilder()
				.dataSource(dataSource)
				.build();


		EmbeddingService embeddingService = new EmbeddingService(embeddingModel);
		VectorService vectorService = new VectorService(embeddingStore, embeddingModel, dataSource, storeConfig);

		routing.register("/api/similarity", new SimilarityService(config.get("ui.font.zoom.default"), embeddingService, vectorService));
	}

	private static String resolveOpenAiApiKey(String configuredApiKey) {
		if (isUsableApiKey(configuredApiKey)) {
			return configuredApiKey.trim();
		}

		String envApiKey = System.getenv("OPENAI_API_KEY");
		if (isUsableApiKey(envApiKey)) {
			return envApiKey.trim();
		}

		throw new IllegalStateException(
				"Missing OpenAI API key. Set 'openai.api-key' in config/config-prod.yaml or OPENAI_API_KEY environment variable.");
	}

	private static boolean isUsableApiKey(String value) {
		if (value == null) {
			return false;
		}
		String normalized = value.trim();
		return !normalized.isEmpty()
				&& !"demo".equalsIgnoreCase(normalized)
				&& !normalized.contains("${");
	}
}
