package com.example.document.service;

import static org.mockito.Mockito.*;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class StartupServiceTest {

    private StartupService startupService;

    @InjectMock
    private DocumentService documentService;


    @BeforeEach
    void setUp() {
        startupService = new StartupService();
        startupService.documentService = documentService;
    }

    @AfterEach
    void tearDown() {
        startupService = null;
    }

    @Test
    void onStartWhenLoadDemoDataTrue() {
        startupService.loadDemoData = true;

        startupService.onStart(null);

        verify(documentService, times(1)).wipeAllEmbeddings();
        verify(documentService, times(1)).embedLoadedDocuments();

    }

    @Test
    void onStartWhenLoadDemoDataFalse() {
        startupService.loadDemoData = false;
        startupService.onStart(null);
        verifyNoInteractions(documentService);
    }
}
