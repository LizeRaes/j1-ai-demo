package com.example.document.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DocumentAccessPolicyServiceTest {

    private static final String SAMPLE_ACCESS_POLICY_YAML = """
            DocA.txt:
              read: [team1, team2]
            DocB.md:
              read: [team2]
            DocC.pdf:
              read: []
            """;

    private DocumentAccessPolicyService service;
    private Path tempConfigDir;

    @BeforeEach
    void setup() throws Exception {
        tempConfigDir = Files.createTempDirectory("access-policy-test");
        Path configPath = tempConfigDir.resolve("config").resolve("document_access_policy.yaml");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, SAMPLE_ACCESS_POLICY_YAML);

        service = new DocumentAccessPolicyService();
        service.accessConfigPath = configPath.toString();
        service.init();
    }

    @Test
    void loadsYamlPoliciesWithExpectedTeams() {
        Map<String, List<String>> policies = service.getAllAccessPolicies();

        assertEquals(3, policies.size());
        assertEquals(List.of("team1", "team2"), policies.get("DocA.txt"));
        assertEquals(List.of("team2"), policies.get("DocB.md"));
        assertEquals(List.of(), policies.get("DocC.pdf"));
    }

    @Test
    void getAccessTeamsReturnsConfiguredTeamsForKnownDocument() {
        List<String> teams = service.getAccessTeams("DocA.txt");
        assertEquals(List.of("team1", "team2"), teams);
    }

    @Test
    void getAccessTeamsReturnsEmptyListForUnknownDocument() {
        List<String> teams = service.getAccessTeams("NonExistentDocument.txt");
        assertNotNull(teams);
        assertTrue(teams.isEmpty());
    }

    @Test
    void updateDocumentAccessWithTeams() {
        String documentName = "TestDocument.txt";
        List<String> teams = List.of("team1", "team2");
        service.updateDocumentAccess(documentName, teams);
        assertEquals(teams, service.getAccessTeams(documentName));
    }

    @Test
    void updateDocumentAccessPersistsToYaml() throws Exception {
        String documentName = "PersistedDoc.txt";
        List<String> teams = List.of("teamA", "teamB");
        service.updateDocumentAccess(documentName, teams);

        DocumentAccessPolicyService service2 = new DocumentAccessPolicyService();
        service2.accessConfigPath = service.accessConfigPath;
        service2.init();
        assertEquals(teams, service2.getAccessTeams(documentName));
    }

    @Test
    void missingFileTreatsAsNoRbac() throws Exception {
        Path nonExistentPath = tempConfigDir.resolve("nonexistent").resolve("document_access_policy.yaml");

        DocumentAccessPolicyService svc = new DocumentAccessPolicyService();
        svc.accessConfigPath = nonExistentPath.toString();
        svc.init();

        assertTrue(svc.getAllAccessPolicies().isEmpty());
        assertTrue(svc.getAccessTeams("AnyDoc.txt").isEmpty());
    }

    @Test
    void removeDocumentPersistsToYaml() throws Exception {
        String documentName = "ToRemove.txt";
        service.updateDocumentAccess(documentName, List.of("teamX"));
        service.removeDocument(documentName);

        DocumentAccessPolicyService service2 = new DocumentAccessPolicyService();
        service2.accessConfigPath = service.accessConfigPath;
        service2.init();
        assertTrue(service2.getAccessTeams(documentName).isEmpty());
    }

    @Test
    void updateDocumentAccessWithEmptyOrNullTeamsMeansCompanyWide() {
        String documentName = "TestDocument.txt";
        service.updateDocumentAccess(documentName, List.of());
        assertTrue(service.getAccessTeams(documentName).isEmpty());

        service.updateDocumentAccess(documentName, null);
        assertTrue(service.getAccessTeams(documentName).isEmpty());
    }

    @Test
    void removeDocument() {
        String documentName = "TestDocument.txt";
        List<String> teams = List.of("team1", "team2");
        service.updateDocumentAccess(documentName, teams);
        assertFalse(service.getAccessTeams(documentName).isEmpty());

        service.removeDocument(documentName);
        assertTrue(service.getAccessTeams(documentName).isEmpty());
    }

    @AfterEach
    void tearDown() throws Exception {
        service = null;
        if (tempConfigDir != null && Files.exists(tempConfigDir)) {
            try (var paths = Files.walk(tempConfigDir)) {
                paths.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) { }
                });
            }
        }
    }
}
