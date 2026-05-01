package com.example.appointment.service;

import com.example.appointment.dto.TriageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OciGenAiConnectionSmokeTest {

    private static final String[] TEST_PROPERTIES = {
            "ai-triage.llm.provider",
            "ai-triage.llm.oci.auth-method",
            "ai-triage.llm.oci.config-file",
            "ai-triage.llm.oci.profile",
            "ai-triage.llm.oci.compartment-id",
            "ai-triage.llm.oci.region",
            "ai-triage.llm.oci.chat-model.family",
            "ai-triage.llm.oci.chat-model.model-name",
            "ai-triage.llm.oci.chat-model.serving-type",
            "ai-triage.llm.oci.chat-model.temperature",
            "ai-triage.llm.oci.chat-model.max-tokens"
    };

    @AfterEach
    void clearProperties() {
        for (String key : TEST_PROPERTIES) {
            System.clearProperty(key);
        }
    }

    @Test
    void manualSmokeTest() {
        Assumptions.assumeTrue(Boolean.getBoolean("oci.smoke"),
                "Set -Doci.smoke=true to run the manual OCI GenAI smoke test.");

        System.setProperty("ai-triage.llm.provider", "oci");
        System.setProperty("ai-triage.llm.oci.auth-method", "api_key");
        System.setProperty("ai-triage.llm.oci.config-file",
                Path.of(System.getProperty("user.home"), ".oci", "config").toString());
        System.setProperty("ai-triage.llm.oci.profile", "DEFAULT");
        String compartmentId = System.getenv("OCI_COMPARTMENT_ID");
        Assumptions.assumeTrue(compartmentId != null && !compartmentId.isBlank(),
                "Set OCI_COMPARTMENT_ID to run the manual OCI GenAI smoke test.");
        System.setProperty("ai-triage.llm.oci.compartment-id", compartmentId);
        System.setProperty("ai-triage.llm.oci.region", "us-ashburn-1");
        System.setProperty("ai-triage.llm.oci.chat-model.family", "generic");
        System.setProperty("ai-triage.llm.oci.chat-model.model-name", "xai.grok-4-fast-reasoning");
        System.setProperty("ai-triage.llm.oci.chat-model.serving-type", "ON_DEMAND");
        System.setProperty("ai-triage.llm.oci.chat-model.temperature", "0.2");
        System.setProperty("ai-triage.llm.oci.chat-model.max-tokens", "256");

        ConfigurableAiTriageAssistant assistant = new ConfigurableAiTriageAssistant();
        assistant.init();
        try {
            var result = assistant.triage(
                    "Customer was charged twice and wants a refund.",
                    List.of(new TriageRequest.TicketTypeInfo("billing", "Billing issues")));

            assertNotNull(result);
            assertNotNull(result.ticketType());
            assertNotNull(result.aiConfidencePercent());
            assertTrue(result.aiConfidencePercent() >= 0 && result.aiConfidencePercent() <= 100);
        } finally {
            assistant.destroy();
        }
    }
}
