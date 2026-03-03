package com.example.ticket.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel model;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void testConstructor() {
        assertNotNull(embeddingService);
    }

    @Test
    void testEmbedSuccess() {
        float[] expectedVector = {1.0f, 2.0f};
        Embedding embedding = new Embedding(expectedVector);
        when(model.embed(anyString())).thenReturn(Response.from(embedding));

        float[] result = embeddingService.embed("test text");

        assertArrayEquals(expectedVector, result);
        verify(model, times(1)).embed(anyString());
    }

    @Test
    void testEmbedFailure() {
        when(model.embed(anyString())).thenThrow(new RuntimeException("Embedding failed"));

        // Act and Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> embeddingService.embed("test text"));
        assertEquals("Embedding failed", exception.getMessage());
        verify(model, times(1)).embed(anyString());
    }

    @Test
    void testEmbedNullText() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> embeddingService.embed(null));
        assertNotNull(exception);
        verify(model, never()).embed(anyString());
    }
}
