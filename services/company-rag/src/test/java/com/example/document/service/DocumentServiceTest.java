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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private Path tempDocumentsDir;

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
    LogService logService;

    @Mock
    EmbeddingStore<TextSegment> embeddingStore;

    @InjectMocks
    DocumentService service;

    @BeforeEach
    void setUp() {
        try {
            tempDocumentsDir = Files.createTempDirectory("company-rag-docs");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        service.documentsDir = tempDocumentsDir.toString();
        service.splitConfigLocation = "classpath:/config/document_splitting_rule.yaml";
        service.defaultStrategy = "recursive";
        service.preprocessingMode = "pure-text";
        service.doclingBaseUrl = "http://localhost:5001";
        service.doclingRetryMaxAttempts = 1;
        service.doclingRetryInitialDelayMs = 0L;

        when(vectorDatabaseConfig.getEmbeddingStore()).thenReturn(embeddingStore);
        when(vectorDatabaseConfig.getEmbeddingTable()).thenReturn("document");
        when(vectorDatabaseConfig.getMetadataColumn()).thenReturn("metadata");

        service.init();
    }

    @Test
    void upsertDocument() {
        TextSegment s1 = TextSegment.from("seg1");
        TextSegment s2 = TextSegment.from("seg2");
        when(chunkingService.chunkDocument(any(Document.class), eq("TestDoc.txt"), anyString()))
                .thenReturn(List.of(s1, s2));

        Response<Embedding> resp = mock(Response.class);
        when(resp.content()).thenReturn(Embedding.from(new float[]{0.1f, 0.2f}));
        when(embeddingModel.embed(anyString())).thenReturn(resp);

        service.upsertDocumentFile("TestDoc.txt", "content here".getBytes(StandardCharsets.UTF_8), List.of("teamX"));

        verify(accessPolicyService).updateDocumentAccess("TestDoc.txt", List.of("teamX"));
        verify(embeddingStore).removeAll(any(Filter.class));
        verify(accessPolicyService, never()).removeDocument("TestDoc.txt");
        verify(embeddingStore, times(2)).add(any(Embedding.class), any(TextSegment.class));

        ArgumentCaptor<String> strategyCap = ArgumentCaptor.forClass(String.class);
        verify(chunkingService).chunkDocument(any(Document.class), eq("TestDoc.txt"), strategyCap.capture());
        assertEquals("recursive", strategyCap.getValue()); // default from setUp
        assertTrue(Files.exists(tempDocumentsDir.resolve("TestDoc.txt")));
    }

    @Test
    void upsertUnsupportedDoclingFileStoresButSkipsEmbedding() {
        service.preprocessingMode = "docling";

        assertDoesNotThrow(() -> service.upsertDocumentFile(
                "fake-seizure.annotations.json",
                "{\"label\":\"demo\"}".getBytes(StandardCharsets.UTF_8),
                List.of("teamX")
        ));

        assertTrue(Files.exists(tempDocumentsDir.resolve("fake-seizure.annotations.json")));
        verify(accessPolicyService).updateDocumentAccess("fake-seizure.annotations.json", List.of("teamX"));
        verify(embeddingStore).removeAll(any(Filter.class));
        verify(chunkingService, never()).chunkDocument(any(Document.class), eq("fake-seizure.annotations.json"), anyString());
        verify(logService).addLog(contains("stored but not embedded"), eq("warn"));
    }

    @Test
    void findAllDocuments() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        Connection connection = mock(Connection.class);
        when(accessPolicyService.getAllAccessPolicies()).thenReturn(Map.of("PolicyDoc", List.of("teamA")));
        when(accessPolicyService.getAccessTeams("PolicyDoc")).thenReturn(List.of("teamA"));
        when(accessPolicyService.getAccessTeams("DBDoc1")).thenReturn(List.of());
        when(accessPolicyService.getAccessTeams("DBDoc2")).thenReturn(List.of());

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
        assertTrue(
                names.stream()
                        .filter(d -> "PolicyDoc".equals(d.documentName()))
                        .findFirst()
                        .map(d -> List.of("teamA").equals(d.rbacTeams()))
                        .orElse(false)
        );
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

    @Test
    void deleteDocumentRemovesFileAndEmbeddings() throws Exception {
        Path docPath = tempDocumentsDir.resolve("DeleteMe.txt");
        Files.writeString(docPath, "content");

        service.deleteDocument("DeleteMe.txt");

        assertFalse(Files.exists(docPath));
        verify(embeddingStore).removeAll(any(Filter.class));
        verify(accessPolicyService).removeDocument("DeleteMe.txt");
    }

    @Test
    void extractContentForChunkingPureTextReturnsLiteralContent() throws Exception {
        Path tmp = Files.createTempFile("doc", ".txt");
        String literal = "Line 1\nTable|Cell";
        Files.writeString(tmp, literal);
        try {
            service.preprocessingMode = "pure-text";
            String extracted = service.extractContentForChunking(tmp);
            assertEquals(literal, extracted);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void extractContentForChunkingDoclingModeKeepsTxtAsPlainText() throws Exception {
        Path tmp = Files.createTempFile("docling-txt", ".txt");
        String literal = "literal txt content";
        Files.writeString(tmp, literal);
        try {
            service.preprocessingMode = "docling";
            service.doclingBaseUrl = "http://localhost:1";

            String extracted = service.extractContentForChunking(tmp);

            assertEquals(literal, extracted);
            verify(logService, never()).addLog(contains("falling back to plain text"), anyString());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void extractContentForChunkingDoclingModeSendsMdToDocling() throws Exception {
        Path tmp = Files.createTempFile("docling-md", ".md");
        String literal = "# Heading\n- bullet";
        Files.writeString(tmp, literal);
        try {
            service.preprocessingMode = "docling";
            service.doclingBaseUrl = "http://localhost:1";

            DocumentService.SkipDocumentException exception = assertThrows(
                    DocumentService.SkipDocumentException.class,
                    () -> service.extractContentForChunking(tmp)
            );
            assertTrue(exception.getMessage().contains("Docling failed for"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void embedLocalDocumentsSkipsNonTxtWhenDoclingFails() throws Exception {
        Path dir = Files.createTempDirectory("docling-skip");
        Path txt = dir.resolve("ok.txt");
        Path pdf = dir.resolve("skip.pdf");
        Files.writeString(txt, "txt content");
        Files.writeString(pdf, "fake pdf bytes");

        try {
            service.documentsDir = dir.toString();
            service.preprocessingMode = "docling";
            service.doclingBaseUrl = "http://localhost:1";

            when(chunkingService.chunkDocument(any(Document.class), eq("ok.txt"), anyString()))
                    .thenReturn(List.of(TextSegment.from("txt content")));
            Response<Embedding> r = mock(Response.class);
            when(r.content()).thenReturn(Embedding.from(new float[]{0.5f}));
            when(embeddingModel.embed(anyString())).thenReturn(r);

            service.embedLocalDocuments();

            verify(embeddingStore, times(1)).add(any(Embedding.class), any(TextSegment.class));
            verify(logService, atLeastOnce()).addLog(contains("skipped (non-txt file)"), eq("warn"));
        } finally {
            Files.deleteIfExists(txt);
            Files.deleteIfExists(pdf);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void embedLocalDocumentsPureTextModeLoadsOnlyTxtAndMd() throws Exception {
        Path dir = Files.createTempDirectory("puretext-only");
        Path txt = dir.resolve("ok.txt");
        Path md = dir.resolve("notes.md");
        Path pdf = dir.resolve("skip.pdf");
        Files.writeString(txt, "txt content");
        Files.writeString(md, "md content");
        Files.writeString(pdf, "fake pdf bytes");

        try {
            service.documentsDir = dir.toString();
            service.preprocessingMode = "pure-text";

            when(chunkingService.chunkDocument(any(Document.class), eq("ok.txt"), anyString()))
                    .thenReturn(List.of(TextSegment.from("txt content")));
            when(chunkingService.chunkDocument(any(Document.class), eq("notes.md"), anyString()))
                    .thenReturn(List.of(TextSegment.from("md content")));
            Response<Embedding> r = mock(Response.class);
            when(r.content()).thenReturn(Embedding.from(new float[]{0.5f}));
            when(embeddingModel.embed(anyString())).thenReturn(r);

            service.embedLocalDocuments();

            verify(embeddingStore, times(2)).add(any(Embedding.class), any(TextSegment.class));
            verify(logService, atLeastOnce()).addLog(contains("non-text file in pure-text mode"), eq("warn"));
        } finally {
            Files.deleteIfExists(txt);
            Files.deleteIfExists(md);
            Files.deleteIfExists(pdf);
            Files.deleteIfExists(dir);
        }
    }

    @AfterEach
    void tearDown() {
        if (tempDocumentsDir != null) {
            try (var paths = Files.walk(tempDocumentsDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
        vectorDatabaseConfig = null;
        dataSource = null;
        chunkingService = null;
        accessPolicyService = null;
        logService = null;
        embeddingStore = null;
        service = null;
    }
}
