package com.example.ticket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DemoDataServiceTest {

    @Mock
    private VectorService vectorService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private LogService logService;

    @InjectMocks
    private DemoDataService demoDataService;

    @Test
    void testLoadDemoDataSuccess() {
        
        when(embeddingService.embed(anyString())).thenReturn(new float[]{1.0f, 2.0f});
        doNothing().when(vectorService).upsertTicket(any(), any(), any(), any());

        demoDataService.loadDemoData();

        verify(vectorService, times(1)).deleteAllTickets();
        verify(logService, times(7)).addLog(anyString(), anyString());
        verify(vectorService, atLeastOnce()).upsertTicket(any(), any(), any(), any());
    }

    @Test
    void testLoadDemoDataFails() {
        doThrow(new RuntimeException("Failed to delete all tickets")).when(vectorService).deleteAllTickets();
        
        RuntimeException exception = assertThrows(RuntimeException.class, demoDataService::loadDemoData);

        assertTrue(exception.getMessage().contains("Failed to delete all tickets"));
        verify(logService, times(1)).addLog(anyString(), anyString());
    }
}
