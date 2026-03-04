package com.example.appointment.service;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceTest {

    @Test
    void triageShouldClampValues() {
        AiService service = new AiService();
        service.aiTriageAssistant = (userMessage, allowed) -> new AiTriageResult("billing", 99, -5);

        AiTriageResult result = service.triage("hello", List.of(new TriageRequest.TicketTypeInfo("billing", "Billing request")));

        assertEquals("billing", result.ticketType());
        assertEquals(10, result.urgencyScore());
        assertEquals(0, result.aiConfidencePercent());
    }

    @Test
    void triageShouldWrapFailures() {
        AiService service = new AiService();
        service.aiTriageAssistant = (userMessage, allowed) -> {
            throw new IllegalStateException("boom");
        };

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.triage("hello", List.of()));

        assertTrue(ex.getMessage().startsWith("AI_TRIAGE_FAILED:"));
    }
}