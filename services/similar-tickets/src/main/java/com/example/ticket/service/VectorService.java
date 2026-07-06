package com.example.ticket.service;

import com.example.ticket.model.SimilarTicket;
import com.example.ticket.model.TicketData;
import com.example.ticket.model.TicketSearchQuery;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service.Singleton
public class VectorService {

    private static final System.Logger log = System.getLogger(LogService.LOGGER_NAME);
    private static final String EMBEDDING_TABLE_CONFIG_PATH =
            "langchain4j.embedding-stores.oracle-embedding-store.embedding-table";
    private static final String RETRIEVE_ALL_QUERY = """
                SELECT
                  JSON_VALUE(%s, '$.id' RETURNING NUMBER) AS ticket_id,
                  JSON_VALUE(%s, '$.type' RETURNING VARCHAR2(200)) AS ticket_type,
                  %s AS text_col,
                  %s AS embedding_col
                FROM %s
                """;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DataSource dataSource;
    private final String tableName;
    private final String embeddingColumn;
    private final String metadataColumn;
    private final String textColumn;

    @Service.Inject
    VectorService(@Service.Named("oracle-embedding-store") EmbeddingStore<TextSegment> embeddingStore,
                  @Service.Named("ticket-embedding-model") EmbeddingModel embeddingModel,
                  Config config,
                  DataSource dataSource) {
        this.embeddingStore = Objects.requireNonNull(embeddingStore);
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
        this.dataSource = Objects.requireNonNull(dataSource);

        var tableConfig = embeddingTableConfig(config);
        this.tableName = tableConfig.get("name").asString().orElseThrow();
        this.embeddingColumn = tableConfig.get("embedding-column").asString().orElse("EMBEDDING");
        this.metadataColumn = tableConfig.get("metadata-column").asString().orElse("METADATA");
        this.textColumn = tableConfig.get("text-column").asString().orElse("TEXT");
    }

    private static Config embeddingTableConfig(Config config) {
        return config.get(EMBEDDING_TABLE_CONFIG_PATH);
    }

    public void upsertTicket(TicketData ticket) {
        deleteTicket(ticket.ticketId());

        TextSegment segment = TextSegment.from(
                ticket.text(),
                Metadata.from(
                        Map.of(
                                "id", ticket.ticketId(),
                                "type", ticket.ticketType(),
                                "text", ticket.text()
                        )
                )
        );

        embeddingStore.add(new Embedding(ticket.vector()), segment);
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

    public List<SimilarTicket> searchSimilar(TicketSearchQuery query) {

        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();
        Filter filter = metadataKey("id").isNotEqualTo(query.excludeTicketId());

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(query.maxResults())
                .minScore(query.minScore())
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        return result.matches().stream()
                .map(match -> {
                    Map<String, Object> meta = match.embedded().metadata().toMap();
                    Object idObj = meta.get("id");
                    return switch (idObj) {
                        case Number n -> new SimilarTicket(n.longValue(), match.score());
                        case String s -> new SimilarTicket(Long.parseLong(s), match.score());
                        case Object _ -> null;
                    };
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<TicketData> retrieveAllTickets() {
        String sql = RETRIEVE_ALL_QUERY.formatted(metadataColumn, metadataColumn, textColumn, embeddingColumn, tableName);

        List<TicketData> results = new ArrayList<>();

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Long ticketId = rs.getLong("ticket_id");
                String ticketType = rs.getString("ticket_type");
                String text = rs.getString("text_col");
                float[] vector = rs.getObject("embedding_col", float[].class);
                results.add(new TicketData(ticketId, ticketType, text != null ? text : "N/A", vector, 0L));
            }
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Failed to retrieve tickets from the database", e);
            throw new RuntimeException("Failed to retrieve tickets from the database", e);
        }

        return results;
    }
}
