package com.example.document.service;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class DocumentChunkingServiceTest {

    private DocumentChunkingService service;

    @BeforeEach
    void setup() {
        service = new DocumentChunkingService();
        service.defaultChunkSize = 300;
    }

    @Test
    void testChunkDocumentWithApprovedResponseSplitter() {
        Document document = Document.from("Template: Test template 1\nTemplate: Test template 2");
        String documentName = "test.txt";
        String strategy = "approved-response-splitter";

        List<TextSegment> segments = service.chunkDocument(document, documentName, strategy);

        assertNotNull(segments);
        assertEquals(2, segments.size());
        assertEquals("Template: Test template 1", segments.get(0).text().trim());
        assertEquals("Template: Test template 2", segments.get(1).text().trim());
    }

    @Test
    void testChunkDocumentWithRecursiveSplitter() {
        Document document = Document.from("This is a test document. It has multiple sentences.");
        String documentName = "test.txt";
        String strategy = "recursive";

        List<TextSegment> segments = service.chunkDocument(document, documentName, strategy);

        assertNotNull(segments);
        assertFalse(segments.isEmpty());
    }

    @AfterEach
    void tearDown() {
        service = null;
    }
}
