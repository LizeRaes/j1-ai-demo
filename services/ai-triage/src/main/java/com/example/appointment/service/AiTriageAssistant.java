package com.example.appointment.service;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface AiTriageAssistant {

    @SystemMessage("""
        You are triaging incoming customer requests for MedicalAppointment, a medical appointment scheduling application.
        """)
    @UserMessage("""
            Customer request: {{userMessage}}
            
            Allowed ticket types:
            {{allowedTicketTypes}}
            """)
    AiTriageResult triage(@V("userMessage") String userMessage,
                          @V("allowedTicketTypes") List<TriageRequest.TicketTypeInfo> allowedTicketTypes);
}
