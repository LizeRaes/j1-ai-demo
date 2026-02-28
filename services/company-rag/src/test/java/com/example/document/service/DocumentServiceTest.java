package com.example.document.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.document.config.VectorDatabaseConfig;
import com.example.document.dto.DocumentsResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    VectorDatabaseConfig vectorDatabaseConfig;

    @Mock
    DataSource dataSource;
    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    DocumentChunkingService chunkingService;

    @Mock
    DocumentAccessPolicyService accessPolicyService;

    @Mock
    EmbeddingStore<TextSegment> embeddingStore;

    @InjectMocks
    DocumentService service;

    @BeforeEach
    void setUp() {
        service.documentsDir = "classpath:/documents";
        service.splitConfigLocation = "classpath:/config/document_splitting_rule.yaml";
        service.defaultStrategy = "recursive";

        when(vectorDatabaseConfig.getEmbeddingStore()).thenReturn(embeddingStore);
        when(vectorDatabaseConfig.getEmbeddingTable()).thenReturn("document");
        when(vectorDatabaseConfig.getMetadataColumn()).thenReturn("metadata");

        service.init();
    }

    @Test
    void upsertDocument() {
        TextSegment s1 = TextSegment.from("seg1");
        TextSegment s2 = TextSegment.from("seg2");
        when(chunkingService.chunkDocument(any(Document.class), eq("TestDoc"), anyString()))
                .thenReturn(List.of(s1, s2));

        Response<Embedding> resp = mock(Response.class);
        when(resp.content()).thenReturn(Embedding.from(new float[]{0.1f, 0.2f}));
        when(embeddingModel.embed(anyString())).thenReturn(resp);

        service.upsertDocument("TestDoc", "content here", List.of("teamX"));

        verify(accessPolicyService).updateDocumentAccess("TestDoc", List.of("teamX"));
        verify(embeddingStore).removeAll(any(Filter.class));
        verify(embeddingStore, times(2)).add(any(Embedding.class), any(TextSegment.class));

        ArgumentCaptor<String> strategyCap = ArgumentCaptor.forClass(String.class);
        verify(chunkingService).chunkDocument(any(Document.class), eq("TestDoc"), strategyCap.capture());
        assertEquals("recursive", strategyCap.getValue()); // default from setUp
    }

    @Test
    void findAllDocuments() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        Connection connection = mock(Connection.class);
        when(accessPolicyService.getAllAccessPolicies()).thenReturn(Map.of("PolicyDoc", List.of("teamA")));

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn("DBDoc1", "DBDoc2");

        List<DocumentsResponse.DocumentInfo> names = service.findAllDocuments();
        assertTrue(names.stream()
                .map(DocumentsResponse.DocumentInfo::documentName)
                .toList()
                .containsAll(List.of("PolicyDoc", "DBDoc1", "DBDoc2")));
    }

    @Test
    void wipeAllEmbeddingsFallsBackTruncate() throws Exception {
        Connection connection = mock(Connection.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("TRUNCATE TABLE document"))
                .thenThrow(new RuntimeException("boom"));
        PreparedStatement deletePs = mock(PreparedStatement.class);
        when(connection.prepareStatement("DELETE FROM document")).thenReturn(deletePs);
        when(deletePs.executeUpdate()).thenReturn(3);

        service.wipeAllEmbeddings();

        verify(connection).prepareStatement("TRUNCATE TABLE document");
    }

    @Test
    void embedLoadedDocument() throws Exception {
        Path tmp = Files.createTempFile("doc", ".txt");
        Files.writeString(tmp, "Hello world");
        try {
            when(chunkingService.chunkDocument(any(Document.class), eq(tmp.getFileName().toString()), anyString()))
                    .thenReturn(List.of(TextSegment.from("Hello world")));
            Response<Embedding> r = mock(Response.class);
            when(r.content()).thenReturn(Embedding.from(new float[]{0.5f}));
            when(embeddingModel.embed(anyString())).thenReturn(r);

            service.embedLoadedDocument(tmp);

            verify(embeddingStore).add(any(Embedding.class), any(TextSegment.class));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void deleteDocumentEmbeddings() {
        service.deleteDocumentEmbeddings("DocX");
        verify(embeddingStore).removeAll(any(Filter.class));
    }

    @AfterEach
    void tearDown() {
        vectorDatabaseConfig = null;
        dataSource = null;
        chunkingService = null;
        accessPolicyService = null;
        embeddingStore = null;
        service = null;
    }
}
