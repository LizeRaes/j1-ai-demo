package com.example.document.service;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class DocumentAccessPolicyServiceTest {

    private DocumentAccessPolicyService service;

    @BeforeEach
    void setup() {
        service = new DocumentAccessPolicyService();
        service.init();
    }

    @Test
    void testGetAccessTeams_DocumentExists() {
        List<String> teams = service.getAccessTeams("Approved_Response_Templates.txt");
        assertNotNull(teams);
    }

    @Test
    void testGetAccessTeams_DocumentDoesNotExist() {
        List<String> teams = service.getAccessTeams("NonExistentDocument.txt");
        assertNotNull(teams);
        assertTrue(teams.isEmpty());
    }

    @Test
    void testUpdateDocumentAccess_WithTeams() {
        String documentName = "TestDocument.txt";
        List<String> teams = List.of("Team1", "Team2");
        service.updateDocumentAccess(documentName, teams);
        assertEquals(teams, service.getAccessTeams(documentName));
    }

    @Test
    void testUpdateDocumentAccess_EmptyTeams() {
        String documentName = "TestDocument.txt";
        service.updateDocumentAccess(documentName, List.of());
        assertTrue(service.getAccessTeams(documentName).isEmpty());
    }

    @Test
    void testUpdateDocumentAccess_NullTeams() {
        String documentName = "TestDocument.txt";
        service.updateDocumentAccess(documentName, null);
        assertTrue(service.getAccessTeams(documentName).isEmpty());
    }

    @Test
    void testRemoveDocument() {
        String documentName = "TestDocument.txt";
        List<String> teams = List.of("Team1", "Team2");
        service.updateDocumentAccess(documentName, teams);
        assertFalse(service.getAccessTeams(documentName).isEmpty());
        service.removeDocument(documentName);
        assertTrue(service.getAccessTeams(documentName).isEmpty());
    }

    @Test
    void testGetAllAccessPolicies() {
        Map<String, List<String>> list = service.getAllAccessPolicies();
        assertNotNull(list);
        assertFalse(list.isEmpty());
    }

    @AfterEach
    void tearDown() {
        service = null;
    }
}

