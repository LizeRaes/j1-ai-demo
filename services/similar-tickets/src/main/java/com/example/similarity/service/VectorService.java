package com.example.ticket.service;

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

import com.example.ticket.dto.TicketsResponse;

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

    public void upsertTicket(Long ticketId, String ticketType, String text, float[] vector) {
        deleteTicket(ticketId);

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

    public void deleteTicket(Long ticketId) {
        embeddingStore.removeAll(metadataKey("id").isEqualTo(ticketId));
    }

    public void deleteAllTickets() {
        String sql = "TRUNCATE TABLE %s".formatted(tableName);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        } catch (Exception truncateFailed) {
            // Fallback if TRUNCATE isn't allowed due to privileges
            String delete = "DELETE FROM %s".formatted(tableName);
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(delete)) {
                ps.executeUpdate();
            } catch (Exception deleteFailed) {
                throw new RuntimeException("Failed to delete all tickets from the database", deleteFailed);
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
					return switch (idObj) {
						case Number n -> new SearchResult(n.longValue(), match.score());
						case String s -> new SearchResult(Long.parseLong(s), match.score());
						case Object _ -> null;
					};
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<TicketsResponse.TicketInfo> retrieveAllTickets() {
        String sql = """
                SELECT
                  JSON_VALUE(%s, '$.id' RETURNING NUMBER) AS ticket_id,
                  JSON_VALUE(%s, '$.type' RETURNING VARCHAR2(200)) AS ticket_type,
                  %s AS text_col,
                  %s AS embedding_col
                FROM %s
                """.formatted(metadataColumn, metadataColumn, textColumn, embeddingColumn, tableName);

        List<TicketsResponse.TicketInfo> results = new ArrayList<>();

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Long ticketId = rs.getLong("ticket_id");
                String ticketType = rs.getString("ticket_type");
                String text = rs.getString("text_col");
                float[] vector = rs.getObject("embedding_col", float[].class);
                results.add(new TicketsResponse.TicketInfo(ticketId, ticketType, text != null ? text : "N/A", vector));
            }
        } catch (Exception e) {
            return List.of();
        }

        return results;
    }

    public record SearchResult(Long ticketId, double score) {}
}
