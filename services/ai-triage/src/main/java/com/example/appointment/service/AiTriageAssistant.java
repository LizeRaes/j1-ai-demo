package com.example.appointment.service;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

import java.util.List;

//@RegisterAiService(maxSequentialToolInvocations = 1)
@RegisterAiService
public interface AiTriageAssistant {

    @SystemMessage("""
        You are triaging incoming customer requests for MedicalAppointment, a medical appointment scheduling application.
        Call the urgency scoring tool exactly once, passing the full customer request text as argument.
        """)
    @UserMessage("""
            Customer request: {{userMessage}}
            
            Allowed ticket types:
            {{allowedTicketTypes}}
            """)
    @McpToolBox("urgency")
    AiTriageResult triage(String userMessage,
                          List<TriageRequest.TicketTypeInfo> allowedTicketTypes);
}