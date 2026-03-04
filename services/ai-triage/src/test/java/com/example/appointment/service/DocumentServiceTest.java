package com.example.appointment.service;

import com.example.appointment.dto.DocumentSearchRequest;
import com.example.appointment.dto.DocumentSearchResponse;
import com.example.appointment.external.DocumentClient;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    DocumentClient documentClient;

    @Mock
    EventLogService eventLogService;

    DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService();
        service.documentClient = documentClient;
        service.eventLogService = eventLogService;
        service.maxResults = 5;
        service.minScore = 0.3;
    }

    @Test
    void searchDocuments() {
        when(documentClient.search(any())).thenReturn(new DocumentSearchResponse(List.of()));

        DocumentSearchResponse response = service.searchDocuments("need help");

        assertNotNull(response);

        ArgumentCaptor<DocumentSearchRequest> captor = ArgumentCaptor.forClass(DocumentSearchRequest.class);
        verify(documentClient).search(captor.capture());
        DocumentSearchRequest captured = captor.getValue();

        assertEquals("need help", captured.originalText());
        assertEquals(5, captured.maxResults());
        assertEquals(0.3, captured.minScore());
    }

    @Test
    void fetchDocumentContent() {
        when(documentClient.fetchDocument("a.md")).thenThrow(new RuntimeException("down"));

        Response response = service.fetchDocumentContent("a.md");

        assertEquals(503, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
    }

    @Test
    void downloadDocument() {
        when(documentClient.downloadDocument("a.pdf"))
                .thenReturn(Response.ok(new byte[]{1, 2, 3})
                        .type("application/pdf")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=a.pdf")
                        .build());

        Response response = service.downloadDocument("a.pdf");

        assertEquals(200, response.getStatus());
        assertEquals("application/pdf", response.getHeaderString(HttpHeaders.CONTENT_TYPE));
        assertEquals("attachment; filename=a.pdf", response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) response.getEntity());
    }

    @AfterEach
    void tearDown() {
        service.documentClient = null;
        service.eventLogService = null;
        service.maxResults = 0;
        service.minScore = 0.0;
        service = null;

    }
}