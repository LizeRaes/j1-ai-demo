package org.example.similarity.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.helidon.integrations.langchain4j.providers.oracle.EmbeddingTableConfig;
import io.helidon.integrations.langchain4j.providers.oracle.OracleEmbeddingStoreConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

public class VectorService {

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final EmbeddingModel embeddingModel;
	private final DataSource dataSource;
	private final String tableName;
	private final String embeddingColumn;
	private final String metadataColumn;
	private final String textColumn;

	public VectorService(EmbeddingStore<TextSegment> embeddingStore,
                         EmbeddingModel embeddingModel,
                         DataSource dataSource,
                         OracleEmbeddingStoreConfig storeConfig) {
		this.embeddingStore = Objects.requireNonNull(embeddingStore);
		this.embeddingModel = Objects.requireNonNull(embeddingModel);
		this.dataSource = Objects.requireNonNull(dataSource);

		EmbeddingTableConfig embeddingTableConfig = storeConfig.embeddingTable().get();

		this.tableName = Objects.requireNonNull(embeddingTableConfig.name().get());
		this.embeddingColumn = embeddingTableConfig.embeddingColumn().orElse("EMBEDDING");
		this.metadataColumn = embeddingTableConfig.metadataColumn().orElse("METADATA");
		this.textColumn = embeddingTableConfig.textColumn().orElse("TEXT");
	}

	public void upsertPoint(Long ticketId, String ticketType, String text, float[] vector) {
		deletePoint(ticketId);

		TextSegment segment = TextSegment.from(
				text,
				Metadata.from(
						Map.of(
								"id", ticketId,
								"type", ticketType,
								"text", text
						)
				)
		);

		embeddingStore.add(new Embedding(vector), segment);
	}

	public void deletePoint(Long ticketId) {
		embeddingStore.removeAll(metadataKey("id").isEqualTo(ticketId));
	}

	public void deleteAllPoints() {
		String sql = "TRUNCATE TABLE " + tableName;

		try (Connection c = dataSource.getConnection();
			 PreparedStatement ps = c.prepareStatement(sql)) {
			ps.execute();
		} catch (Exception truncateFailed) {
			// Fallback if TRUNCATE isn't allowed due to privileges
			String delete = "DELETE FROM " + tableName;
			try (Connection c = dataSource.getConnection();
				 PreparedStatement ps = c.prepareStatement(delete)) {
				ps.executeUpdate();
			} catch (Exception deleteFailed) {
				throw new RuntimeException("Failed to delete all points from the database", deleteFailed);
			}
		}
	}

	public List<SearchResult> searchSimilar(String queryText,
											int maxResults,
											double minScore,
											Long excludeTicketId) {

		Embedding queryEmbedding = embeddingModel.embed(queryText).content();
		Filter filter = metadataKey("id").isNotEqualTo(excludeTicketId);

		EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
				.queryEmbedding(queryEmbedding)
				.filter(filter)
				.maxResults(maxResults)
				.minScore(minScore)
				.build();

		EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

		return result.matches().stream()
				.map(match -> {
					Map<String, Object> meta = match.embedded().metadata().toMap();
					Object idObj = meta.get("id");
					if (idObj instanceof Number n) {
						return new SearchResult(n.longValue(), match.score());
					}
					if (idObj instanceof String s) {
						try {
							return new SearchResult(Long.parseLong(s), match.score());
						} catch (NumberFormatException ignored) {
						}
					}
					return null;
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	public List<TicketPoint> getAllPoints() {
		// TODO, make it smarter
        // Ticket fields are stored in metadata JSON by our upsertPoint().
		// Use JSON_VALUE to extract id & type.
		String sql = """
				SELECT
				  JSON_VALUE(%s, '$.id' RETURNING NUMBER) AS ticket_id,
				  JSON_VALUE(%s, '$.type' RETURNING VARCHAR2(200)) AS ticket_type,
				  %s AS text_col,
				  %s AS embedding_col
				FROM %s
				""".formatted(metadataColumn, metadataColumn, textColumn, embeddingColumn, tableName);

		List<TicketPoint> results = new ArrayList<>();

		try (Connection c = dataSource.getConnection();
			 PreparedStatement ps = c.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {

			while (rs.next()) {
				Long ticketId = rs.getLong("ticket_id");
				if (rs.wasNull()) {
					continue;
				}
				String ticketType = rs.getString("ticket_type");
				String text = rs.getString("text_col");

				float[] vector = readVectorAsFloatArray(rs);

				results.add(new TicketPoint(ticketId, ticketType, text != null ? text : "N/A", vector));
			}
		} catch (Exception e) {
			return List.of();
		}

		return results;
	}

	private float[] readVectorAsFloatArray(ResultSet rs) throws Exception {
		try {
			float[] fa = rs.getObject("embedding_col", float[].class);
			if (fa != null) return fa;
		} catch (Exception ignored) {}

		try {
			double[] da = rs.getObject("embedding_col", double[].class);
			if (da != null) {
				float[] fa = new float[da.length];
				for (int i = 0; i < da.length; i++) fa[i] = (float) da[i];
				return fa;
			}
		} catch (Exception ignored) {}

		Object v = rs.getObject("embedding_col");
		if (v == null) return null;

		if (v instanceof float[] fa2) {
			return fa2;
		}

		try {
			var m = v.getClass().getMethod("getFloatArray");
			return (float[]) m.invoke(v);
		} catch (NoSuchMethodException ignored) {}

		try {
			var m = v.getClass().getMethod("toFloatArray");
			return (float[]) m.invoke(v);
		} catch (NoSuchMethodException ignored) {
		}

		try {
			var m = v.getClass().getMethod("getValues");
			Object values = m.invoke(v);
			if (values instanceof java.util.List<?> list) {
				float[] out = new float[list.size()];
				for (int i = 0; i < list.size(); i++) {
					out[i] = ((Number) list.get(i)).floatValue();
				}
				return out;
			}
		} catch (NoSuchMethodException ignored) {}

		throw new IllegalStateException("Unsupported vector JDBC type: " + v.getClass().getName());
	}

	public record SearchResult(Long ticketId, double score) {}

	public record TicketPoint(Long ticketId, String ticketType, String text, float[] vector) { }
}
