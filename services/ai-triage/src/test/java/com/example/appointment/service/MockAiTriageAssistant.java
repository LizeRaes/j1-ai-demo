package com.example.appointment.service;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@Mock
@ApplicationScoped
public class MockAiTriageAssistant implements AiTriageAssistant {

    @Override
    public AiTriageResult triage(String userMessage, List<TriageRequest.TicketTypeInfo> allowedTicketTypes) {
        String ticketType = allowedTicketTypes == null || allowedTicketTypes.isEmpty()
                ? "OTHER"
                : allowedTicketTypes.get(0).type();
        return new AiTriageResult(ticketType, 85);
    }
}
