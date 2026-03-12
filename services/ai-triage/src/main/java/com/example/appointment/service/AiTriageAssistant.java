package com.example.appointment.service;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
// import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

import java.util.List;

@RegisterAiService
// @McpToolBox("urgency")
public interface AiTriageAssistant {

    @SystemMessage("""
        You are triaging incoming customer requests for MedicalAppointment, a medical appointment scheduling application.
        """)
    @UserMessage("""
            Customer request: {{userMessage}}
            
            Allowed ticket types:
            {{allowedTicketTypes}}
            """)
    AiTriageResult triage(String userMessage,
                          List<TriageRequest.TicketTypeInfo> allowedTicketTypes);
}