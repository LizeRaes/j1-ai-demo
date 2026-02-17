package com.example.document.service;

import com.example.document.config.VectorDatabaseConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DocumentSearchService {

    private static final Logger LOGGER = Logger.getLogger(DocumentSearchService.class.getName());

    @Inject
    VectorDatabaseConfig vectorDatabaseConfig;

    @Inject
    DataSource dataSource;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    DocumentAccessPolicyService accessPolicyService;


    public List<DocumentSearchResult> searchDocuments(String queryText, int maxResults, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> result = vectorDatabaseConfig.getEmbeddingStore().search(request);

        List<DocumentSearchResult> searchResults = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment segment = match.embedded();
            Map<String, Object> metadata = segment.metadata().toMap();

            String documentName = (String) metadata.get("documentName");
            if (documentName == null) {
                continue;
            }

            String citation = segment.text();
            double score = match.score();
            List<String> rbacTeams = accessPolicyService.getAccessTeams(documentName);

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

    public List<ChunkInfo> getAllChunks() {
        String sql = """
            SELECT
              JSON_VALUE(metadata, '$.documentName') AS document_name,
              JSON_VALUE(metadata, '$.chunkIndex' RETURNING NUMBER) AS chunk_index,
              text AS chunk_text,
              embedding AS chunk_embedding
            FROM %s
        """.formatted(vectorDatabaseConfig.getEmbeddingTable());

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<ChunkInfo> out = new ArrayList<>();

            while (rs.next()) {
                String documentName = rs.getString("document_name");
                if (documentName == null) {
                    continue;
                }

                Integer chunkIndex = null;
                Object chunkIndexObj = rs.getObject("chunk_index");
                if (chunkIndexObj instanceof Number n) {
                    chunkIndex = n.intValue();
                }

                String text = rs.getString("chunk_text");
                if (text == null) text = "N/A";

                float[] vector = rs.getObject("chunk_embedding", float[].class);

                out.add(new ChunkInfo(documentName, chunkIndex, text, vector));
            }

            return out;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting all chunks: ", e);
            return List.of();
        }
    }

    public record DocumentSearchResult(String documentName, String documentLink, String citation, double score,
                                       List<String> rbacTeams) {
    }

    public record ChunkInfo(String documentName, Integer chunkIndex, String text, float[] vector) {
    }
}
