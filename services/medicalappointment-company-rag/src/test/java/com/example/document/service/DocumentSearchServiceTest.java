package com.example.document.service;

import com.example.document.config.VectorDatabaseConfig;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentSearchServiceTest {

    @Mock
    VectorDatabaseConfig vectorDatabaseConfig;

    @Mock
    DataSource dataSource;
    @Mock
    EmbeddingModel embeddingModel;
    @Mock
    DocumentAccessPolicyService accessPolicyService;

    @InjectMocks
    DocumentSearchService service;

    @Test
    void searchDocuments() {
        String query = "test query";

        Response<Embedding> resp = mock(Response.class);
        when(resp.content()).thenReturn(Embedding.from(new float[]{0.1f, 0.2f}));
        when(embeddingModel.embed(query)).thenReturn(resp);

        EmbeddingStore embeddingStore = mock(EmbeddingStore.class);
        when(vectorDatabaseConfig.getEmbeddingStore()).thenReturn(embeddingStore);
        TextSegment segment = TextSegment.from(
                "test text", Metadata.from(Map.of("documentName", "doc1"))
        );
        EmbeddingMatch<TextSegment> match = mock(EmbeddingMatch.class);
        when(match.embedded()).thenReturn(segment);
        when(match.score()).thenReturn(0.87);
        EmbeddingSearchResult<TextSegment> searchResult = mock(EmbeddingSearchResult.class);
        when(searchResult.matches()).thenReturn(List.of(match));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);
        when(accessPolicyService.getAccessTeams("doc1")).thenReturn(List.of("teamA", "teamB"));

        var results = service.searchDocuments(query, 5, 0.5);

        assertEquals(1, results.size());
        var r = results.getFirst();
        assertEquals("doc1", r.documentName());
        assertEquals("/documents/doc1", r.documentLink());
        assertEquals("test text", r.citation());
        assertEquals(0.87, r.score(), 1e-9);
        assertEquals(List.of("teamA", "teamB"), r.rbacTeams());
    }

    @Test
    void getAllChunks() throws Exception {
        when(vectorDatabaseConfig.getEmbeddingTable()).thenReturn("emb_table");

        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);

        when(rs.next()).thenReturn(true, false);
        when(rs.getString("document_name")).thenReturn("doc1");
        when(rs.getObject("chunk_index")).thenReturn(3);
        when(rs.getString("chunk_text")).thenReturn("chunk text");
        when(rs.getObject("chunk_embedding", float[].class)).thenReturn(new float[]{1f, 2f});

        var chunks = service.getAllChunks();
        assertEquals(1, chunks.size());
        var c = chunks.getFirst();
        assertEquals("doc1", c.documentName());
        assertEquals(3, c.chunkIndex());
        assertEquals("chunk text", c.text());
        assertArrayEquals(new float[]{1f, 2f}, c.vector());
    }

    @Test
    void getAllChunksReturnsEmptyList() {
        when(vectorDatabaseConfig.getEmbeddingTable()).thenReturn("emb_table");

        var chunks = service.getAllChunks();
        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @AfterEach
    void tearDown() {
        vectorDatabaseConfig = null;
        dataSource = null;
        accessPolicyService = null;
        service = null;
    }

}
