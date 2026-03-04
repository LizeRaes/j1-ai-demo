package com.example.appointment.service;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

import java.util.List;

@RegisterAiService
public interface AiTriageAssistant {

    @SystemMessage(fromResource = "prompts/system-prompt.txt")
    @UserMessage("""
            Customer request: {{userMessage}}
            
            Allowed ticket types:
            {{allowedTicketTypes}}

            Use available tools when needed.
            
            Classify this request and return the JSON response as specified.
            """)
    @ToolBox(FormatTicket.class)
    AiTriageResult classify(String userMessage,
                            List<TriageRequest.TicketTypeInfo> allowedTicketTypes);
}