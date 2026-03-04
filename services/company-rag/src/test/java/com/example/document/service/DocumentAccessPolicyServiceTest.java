package com.example.document.service;

import static org.junit.jupiter.api.Assertions.*;

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
        service.accessConfigLocation = "classpath:/config/document_access_policy.yaml";
        service.init();
    }

    @Test
    void loadsCurrentYamlPoliciesWithExpectedTeams() {
        Map<String, List<String>> policies = service.getAllAccessPolicies();

        assertEquals(9, policies.size(), "Expected all current YAML documents to be loaded");
        assertEquals(List.of("dispatching", "billing", "scheduling"), policies.get("Approved_Response_Templates.md"));
        assertEquals(List.of("billing", "dispatching"), policies.get("Billing_Refund_Policy.md"));
        assertEquals(List.of("dispatching", "billing", "scheduling"), policies.get("Data_Privacy_User_Data_Handling.txt"));
        assertEquals(List.of("dispatching", "engineering"), policies.get("Known_Bugs_Limitations.md"));
        assertEquals(List.of("engineering"), policies.get("MedicalAppointment_Architecture.txt"));
        assertEquals(List.of("billing"), policies.get("Payment_System_Payment_Flow.txt"));
        assertEquals(List.of("dispatching"), policies.get("Security_Escalation_Policy.md"));
        assertEquals(List.of("billing", "dispatching"), policies.get("Billing_Payment_Reliability_Report_26.pdf"));
        assertEquals(List.of("engineering", "dispatching"), policies.get("Account_Security_Incident_Report_26.pdf"));
    }

    @Test
    void getAccessTeamsReturnsConfiguredTeamsForKnownDocument() {
        List<String> teams = service.getAccessTeams("Known_Bugs_Limitations.md");
        assertEquals(List.of("dispatching", "engineering"), teams);
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
    void tearDown() {
        service = null;
    }
}
