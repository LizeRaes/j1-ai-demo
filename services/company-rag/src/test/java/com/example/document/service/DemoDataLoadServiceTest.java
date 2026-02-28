package com.example.document.service;

import com.example.document.config.VectorDatabaseConfig;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoDataLoadServiceTest {

    @Mock
    DocumentService documentService;
    @Mock
    VectorDatabaseConfig vectorDatabaseConfig;

    @InjectMocks
    DemoDataLoadService demoDataLoadService;

    @BeforeEach
    void setUp() {
        demoDataLoadService.loadDemoData = true;
    }

    @Test
    void onStartWhenDemoDataEnabled() {
        demoDataLoadService.onStart(mock(StartupEvent.class));

        InOrder inOrder = inOrder(vectorDatabaseConfig, documentService);
//        inOrder.verify(vectorDatabaseConfig).getEmbeddingStore();
        inOrder.verify(documentService).wipeAllEmbeddings();
        inOrder.verify(documentService).embedLocalDocuments();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void onStartWhenDemoDataDisabled() {
        demoDataLoadService.loadDemoData = false;

        demoDataLoadService.onStart(mock(StartupEvent.class));

        verifyNoInteractions(documentService);
    }

    @Test
    void onStartWhenVectorStoreInitFails() {
        demoDataLoadService.onStart(mock(StartupEvent.class));

        verify(documentService).wipeAllEmbeddings();
        verify(documentService).embedLocalDocuments();

    }
}
