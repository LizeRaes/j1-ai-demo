package com.example.appointment.service;

import com.example.appointment.dto.SimilaritySearchRequest;
import com.example.appointment.dto.SimilaritySearchResponse;
import com.example.appointment.external.SimilarityClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SimilarityServiceTest {

    @Mock
    SimilarityClient similarityClient;

    SimilarityService service;

    @BeforeEach
    void setUp() {
        service = new SimilarityService();
        service.similarityClient = similarityClient;
        service.maxResults = 7;
        service.minScore = 0.42;
    }

    @Test
    void searchSimilarTickets() {
        when(similarityClient.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SimilaritySearchResponse(List.of(1L, 2L)));

        SimilaritySearchResponse response = service.searchSimilarTickets("billing", "text", 123L);

        assertEquals(List.of(1L, 2L), response.relatedTicketIds());

        ArgumentCaptor<SimilaritySearchRequest> captor = ArgumentCaptor.forClass(SimilaritySearchRequest.class);
        verify(similarityClient).search(captor.capture());
        SimilaritySearchRequest captured = captor.getValue();

        assertEquals("billing", captured.ticketType());
        assertEquals("text", captured.text());
        assertEquals(7, captured.maxResults());
        assertEquals(0.42, captured.minScore());
        assertEquals(123L, captured.ticketId());
    }

    @AfterEach
    void tearDown() {
        service.similarityClient = null;
        service.maxResults = 0;
        service.minScore = 0.0;
        service = null;

    }
}