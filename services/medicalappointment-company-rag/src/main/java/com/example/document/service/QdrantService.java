package com.example.document.service;

import com.example.document.config.QdrantConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@ApplicationScoped
public class QdrantService {

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    QdrantClient qdrantClient;

    @Inject
    QdrantConfig qdrantConfig;

    public void upsertPoint(Long ticketId, String ticketType, String text, float[] vector) {
        TextSegment segment = TextSegment.from(text,
                dev.langchain4j.data.document.Metadata.from(
                        Map.of("ticketId", ticketId, "ticketType", ticketType)
                ));
        embeddingStore.add(new Embedding(vector), segment);
    }

    public void deletePoint(Long ticketId) {
        embeddingStore.removeAll(
                metadataKey("ticketId").isEqualTo(ticketId)
        );
    }

    public List<SearchResult> searchSimilar(
            String queryText,
            int maxResults,
            double minScore,
            Long excludeTicketId
    ) {
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();
        // Only filter to exclude the ticket itself, no ticket type filter
        Filter filter = metadataKey("ticketId").isNotEqualTo(excludeTicketId);

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
                    Object idObj = meta.get("ticketId");
                    if (idObj instanceof Number) {
                        return new SearchResult(((Number) idObj).longValue(), match.score());
                    }
                    return null;
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    public List<TicketPoint> getAllPoints() {
        try {
            qdrantClient.getCollectionInfoAsync(qdrantConfig.getCollectionName()).get();
        } catch (Exception e) {
            return new ArrayList<>();
        }

        try {
            // Use direct Qdrant scroll API - no OpenAI call needed
            Points.ScrollResponse response = qdrantClient.scrollAsync(
                    Points.ScrollPoints.newBuilder()
                            .setCollectionName(qdrantConfig.getCollectionName())
                            .setLimit(10_000)
                            .setWithPayload(
                                    Points.WithPayloadSelector.newBuilder().setEnable(true).build()
                            )
                            .setWithVectors(
                                    Points.WithVectorsSelector.newBuilder().setEnable(true).build()
                            )
                            .build()
            ).get();

            return response.getResultList().stream()
                    .map(p -> {
                        Map<String, JsonWithInt.Value> payload = p.getPayloadMap();

                        // Extract ticketId from payload
                        Long ticketId = getLong(payload.get("ticketId"));
                        if (ticketId == null) {
                            return null;
                        }

                        String ticketType = getString(payload.get("ticketType"));
                        String text = extractTextFromPayload(payload);
                        float[] vector = extractVector(p);

                        return new TicketPoint(ticketId, ticketType, text != null ? text : "N/A", vector);
                    })
                    .filter(tp -> tp != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            // Fallback to empty list if scroll fails
            try {
                Points.ScrollResponse response = qdrantClient.scrollAsync(
                        Points.ScrollPoints.newBuilder()
                                .setCollectionName(qdrantConfig.getCollectionName())
                                .setLimit(10_000)
                                .setWithPayload(
                                        Points.WithPayloadSelector.newBuilder().setEnable(true).build()
                                )
                                .setWithVectors(
                                        Points.WithVectorsSelector.newBuilder().setEnable(true).build()
                                )
                                .build()
                ).get();

                return response.getResultList().stream()
                        .map(p -> {
                            Long ticketId = getLong(p.getPayloadMap().get("ticketId"));
                            String ticketType = getString(p.getPayloadMap().get("ticketType"));
                            // Try to extract text from payload - QdrantEmbeddingStore stores it
                            String text = extractTextFromPayload(p.getPayloadMap());
                            float[] vector = extractVector(p);

                            if (ticketId == null) {
                                return null;
                            }

                            return new TicketPoint(ticketId, ticketType, text != null ? text : "N/A", vector);
                        })
                        .filter(tp -> tp != null)
                        .collect(Collectors.toList());
            } catch (Exception e2) {
                return new ArrayList<>();
            }
        }
    }

    private static String extractTextFromPayload(Map<String, JsonWithInt.Value> payload) {
        // Try common keys where TextSegment text might be stored
        String text = getString(payload.get("text"));
        if (text != null) return text;

        text = getString(payload.get("content"));
        if (text != null) return text;

        // Check if it's stored in a nested structure
        JsonWithInt.Value textValue = payload.get("text");
        if (textValue == null) {
            textValue = payload.get("content");
        }
        if (textValue != null && textValue.hasStructValue()) {
            // If nested, try to extract from the structure
            Map<String, JsonWithInt.Value> struct = textValue.getStructValue().getFieldsMap();
            text = getString(struct.get("text"));
            if (text != null) return text;
            text = getString(struct.get("content"));
            if (text != null) return text;
        }

        return null;
    }

    private static Long getLong(JsonWithInt.Value v) {
        return v != null && v.hasIntegerValue() ? v.getIntegerValue() : null;
    }

    private static String getString(JsonWithInt.Value v) {
        return v != null && v.hasStringValue() ? v.getStringValue() : null;
    }

    private static float[] extractVector(Points.RetrievedPoint p) {
        if (!p.hasVectors() || !p.getVectors().hasVector()) {
            return null;
        }
        List<Float> list = p.getVectors().getVector().getDataList();
        float[] vector = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            vector[i] = list.get(i);
        }
        return vector;
    }

    public static class SearchResult {
        public final Long ticketId;
        public final double score;

        public SearchResult(Long ticketId, double score) {
            this.ticketId = ticketId;
            this.score = score;
        }
    }

    public static class TicketPoint {
        public final Long ticketId;
        public final String ticketType;
        public final String text;
        public final float[] vector;

        public TicketPoint(Long ticketId, String ticketType, String text, float[] vector) {
            this.ticketId = ticketId;
            this.ticketType = ticketType;
            this.text = text;
            this.vector = vector;
        }
    }
}
