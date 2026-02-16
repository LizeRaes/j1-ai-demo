package org.example.similarity;

import io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig;
import io.helidon.service.registry.Services;
import org.example.similarity.dto.DeleteRequest;
import org.example.similarity.dto.LogsResponse;
import org.example.similarity.dto.SearchRequest;
import org.example.similarity.dto.SearchResponse;
import org.example.similarity.dto.StatusResponse;
import org.example.similarity.dto.TicketsResponse;
import org.example.similarity.dto.UpsertRequest;
import org.example.similarity.service.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.media.jackson.JacksonSupport;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


import javax.sql.DataSource;

public class Main {

	public static void main(String[] args) {
		Config config = Config.create();

		int port = config.get("server.port").asInt().orElse(8082);
		String apiKey = config.get("openai.api-key").asString().orElse("");
		String embeddingModelName = config.get("openai.embedding-model").asString().orElse("text-embedding-3-large");
		int defaultZoom = config.get("ui.font.zoom.default").asInt().orElse(100);

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
		TicketStore ticketStore = new TicketStore();
		LogService logService = new LogService();
		VectorService vectorService = new VectorService(embeddingStore, embeddingModel, dataSource, storeConfig);
		DemoDataService demoDataService = new DemoDataService(vectorService, embeddingService, logService);

		// Optionally load demo data on startup
		String demoDataFlag = System.getProperty("DemoData");

		if ("true".equalsIgnoreCase(demoDataFlag)) {
			logService.addLog("DemoData flag detected. Loading demo data and wiping Qdrant DB...", "startup");
			try {
				demoDataService.loadDemoData();
				logService.addLog("Demo data loaded successfully", "startup");
			} catch (Exception e) {
				logService.addLog("Failed to load demo data: " + e.getMessage(), "startup");
				throw e;
			}
		}

		// Routing
		HttpRouting.Builder routing = routing(logService, embeddingService, vectorService, ticketStore, defaultZoom);

		WebServer.builder()
				.config(config.get("server"))
				.routing(routing)
				.mediaContext(it -> it
						.mediaSupportsDiscoverServices(false)
						.addMediaSupport(JacksonSupport.create()).build())
				.build()
				.start();

		System.out.println("Server started at http://localhost:" + port);
	}

	static HttpRouting.Builder routing(LogService logService, EmbeddingService embeddingService, VectorService vectorService, TicketStore ticketStore, int defaultZoom) {
		HttpRouting.Builder routing = HttpRouting.builder();
		routing.post("/api/similarity/tickets/upsert", (req, res) -> {
			try {
				UpsertRequest request = req.content().as(UpsertRequest.class);
				if (request.ticketId() == null || request.text() == null || request.ticketType() == null) {
					res.status(Status.BAD_REQUEST_400).send(Map.of("error", "id, type, and text are required"));
					return;
				}
				logService.addLog("Received ticket #" + request.ticketId() + " via upsert endpoint", "upsert");
				float[] embedding = embeddingService.embed(request.text());
				vectorService.upsertPoint(request.ticketId(), request.ticketType(), request.text(), embedding);
				ticketStore.storeTicket(request.ticketId(), request.ticketType(), request.text(), embedding);
				logService.addLog("Stored embedding for ticket #" + request.ticketId(), "upsert");
				res.header(HeaderNames.CONTENT_TYPE, "application/json").send(new StatusResponse("OK"));
			} catch (Exception e) {
				res.status(Status.BAD_REQUEST_400).send(Map.of("error", e.getMessage()));
			}
		});

		routing.post("/api/similarity/tickets/delete", (req, res) -> {
			try {
				DeleteRequest request = req.content().as(DeleteRequest.class);
				if (request.ticketId() == null) {
					res.status(Status.BAD_REQUEST_400).send(Map.of("error", "id is required"));
					return;
				}
				logService.addLog("Delete request for ticket #" + request.ticketId(), "delete");
				vectorService.deletePoint(request.ticketId());
				ticketStore.removeTicket(request.ticketId());
				res.header(HeaderNames.CONTENT_TYPE, "application/json").send(new StatusResponse("OK"));
			} catch (Exception e) {
				res.status(Status.BAD_REQUEST_400).send(Map.of("error", e.getMessage()));
			}
		});


		routing.post("/api/similarity/tickets/search", (req, res) -> {
			try {
				SearchRequest request = req.content().as(SearchRequest.class);
				if (request.text() == null || request.ticketId() == null) {
					res.status(Status.BAD_REQUEST_400).send(Map.of("error", "text and id are required"));
					return;
				}
				int maxResults = request.maxResults() != null ? request.maxResults() : 5;
				double minScore = request.minScore() != null ? request.minScore() : 0.0;
				logService.addLog("Similarity search for ticket #" + request.ticketId(), "search");
				var searchResults = vectorService.searchSimilar(request.text(), maxResults, minScore, request.ticketId());
				var relatedTicketIds = searchResults.stream().map(VectorService.SearchResult::ticketId).toList();
				String logMessage = "Returned " + searchResults.size() + " similar tickets";
				logService.addLog(logMessage, "search");
				res.header(HeaderNames.CONTENT_TYPE, "application/json").send(new SearchResponse(relatedTicketIds));
			} catch (Exception e) {
				res.status(Status.BAD_REQUEST_400).send(Map.of("error", e.getMessage()));
			}
		});

		routing.get("/api/similarity/tickets/all", (req, res) -> {
			var qdrantPoints = vectorService.getAllPoints();
			var inMemoryTickets = ticketStore.getAllTickets();

            Map<Long, TicketsResponse.TicketInfo> ticketMap = new HashMap<>();
			for (var point : qdrantPoints) {
				ticketMap.put(point.ticketId(), new TicketsResponse.TicketInfo(point.ticketId(), point.ticketType(), point.text(), point.vector()));
			}
			for (var ticket : inMemoryTickets) {
				ticketMap.put(ticket.ticketId(), new TicketsResponse.TicketInfo(ticket.ticketId(), ticket.ticketType(), ticket.text(), ticket.vector()));
			}
			var tickets = ticketMap.values().stream()
					.sorted((a, b) -> Long.compare(b.id(), a.id()))
					.toList();

			res.header(HeaderNames.CONTENT_TYPE, "application/json").send(new TicketsResponse(tickets));
		});

		routing.get("/api/similarity/tickets/logs", (req, res) -> {
			var logs = logService.getLogs().stream()
					.map(l -> new LogsResponse.LogInfo(l.message(), l.type(), l.timestamp()))
					.toList();
			res.header(HeaderNames.CONTENT_TYPE, "application/json").send(new LogsResponse(logs));
		});

		routing.get("/api/similarity/tickets/config", (req, res) -> res.header(HeaderNames.CONTENT_TYPE, "application/json").send(Map.of("defaultZoom", defaultZoom)));

		// Serve OpenAPI YAML at /openapi
		routing.get("/openapi", (req, res) -> {
			try (InputStream is = Main.class.getClassLoader().getResourceAsStream("openapi.yaml")) {
				if (is == null) {
					res.status(Status.NOT_FOUND_404).send("openapi.yaml not found");
					return;
				}
				byte[] bytes = is.readAllBytes();
				res.header(HeaderNames.CONTENT_TYPE, "application/yaml").send(bytes);
			} catch (Exception e) {
				res.status(Status.INTERNAL_SERVER_ERROR_500).send("Failed to load openapi.yaml");
			}
		});
		return routing;
	}
}
