package com.example.document.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class DocumentSearchService {

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    DocumentAccessPolicyService accessPolicyService;

    @Inject
    com.example.document.config.QdrantConfig qdrantConfig;

    @Inject
    io.qdrant.client.QdrantClient qdrantClient;

    /**
     * Searches for similar document chunks based on ticket text.
     *
     * @param queryText The ticket's original text to search for
     * @param maxResults Maximum number of results
     * @param minScore Minimum document score
     * @return List of search results with document links, citations, scores, and RBAC teams
     */
    public List<DocumentSearchResult> searchDocuments(String queryText, int maxResults, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        List<DocumentSearchResult> searchResults = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment segment = match.embedded();
            Map<String, Object> metadata = segment.metadata().toMap();

            String documentName = (String) metadata.get("documentName");
            if (documentName == null) {
                continue;
            }

            String citation = segment.text(); // Whole chunk as citation
            double score = match.score();
            List<String> rbacTeams = accessPolicyService.getAccessTeams(documentName);

            // Create document link (for now, just the document name)
            String documentLink = "/documents/" + documentName;

            searchResults.add(new DocumentSearchResult(
                documentName,
                documentLink,
                citation,
                score,
                rbacTeams
            ));
        }

        return searchResults;
    }

    /**
     * Gets all document chunks with their embeddings.
     * Uses direct Qdrant scroll API to avoid OpenAI API calls.
     */
    public List<ChunkInfo> getAllChunks() {
        try {
            // Use Qdrant scroll API directly - no need for dummy embedding
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

                        // Extract document name from metadata
                        String documentName = getStringFromPayload(payload, "documentName");
                        if (documentName == null) {
                            return null;
                        }

                        // Extract chunk index
                        Integer chunkIndex = null;
                        JsonWithInt.Value chunkIndexValue = payload.get("chunkIndex");
                        if (chunkIndexValue != null && chunkIndexValue.hasIntegerValue()) {
                            chunkIndex = (int) chunkIndexValue.getIntegerValue();
                        }

                        // Extract text - QdrantEmbeddingStore stores it in the payload
                        String text = extractTextFromPayload(payload);
                        if (text == null) {
                            text = "N/A";
                        }

                        // Extract vector
                        float[] vector = extractVector(p);

                        return new ChunkInfo(documentName, chunkIndex, text, vector);
                    })
                    .filter(chunk -> chunk != null && chunk.documentName != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error getting all chunks: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static String getStringFromPayload(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value != null && value.hasStringValue()) {
            return value.getStringValue();
        }
        return null;
    }

    private static String extractTextFromPayload(Map<String, JsonWithInt.Value> payload) {
        // QdrantEmbeddingStore stores TextSegment data in payload
        // Try common keys
        String text = getStringFromPayload(payload, "text");
        if (text != null) return text;

        text = getStringFromPayload(payload, "content");
        if (text != null) return text;

        // Check if stored in nested structure
        JsonWithInt.Value textValue = payload.get("text");
        if (textValue == null) {
            textValue = payload.get("content");
        }
        if (textValue != null && textValue.hasStructValue()) {
            Map<String, JsonWithInt.Value> struct = textValue.getStructValue().getFieldsMap();
            text = getStringFromPayload(struct, "text");
            if (text != null) return text;
            text = getStringFromPayload(struct, "content");
            if (text != null) return text;
        }

        return null;
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

    public static class DocumentSearchResult {
        public final String documentName;
        public final String documentLink;
        public final String citation;
        public final double score;
        public final List<String> rbacTeams;

        public DocumentSearchResult(String documentName, String documentLink, String citation,
                                   double score, List<String> rbacTeams) {
            this.documentName = documentName;
            this.documentLink = documentLink;
            this.citation = citation;
            this.score = score;
            this.rbacTeams = rbacTeams;
        }
    }

    public static class ChunkInfo {
        public final String documentName;
        public final Integer chunkIndex;
        public final String text;
        public final float[] vector;

        public ChunkInfo(String documentName, Integer chunkIndex, String text, float[] vector) {
            this.documentName = documentName;
            this.chunkIndex = chunkIndex;
            this.text = text;
            this.vector = vector;
        }
    }
}
