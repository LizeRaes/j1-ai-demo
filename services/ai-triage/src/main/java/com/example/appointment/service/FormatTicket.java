package com.example.appointment.service;

import com.example.appointment.dto.TriageRequest;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FormatTicket {

    @Tool("Formats allowed ticket types into format-friendly bullet points")
    public String format(List<TriageRequest.TicketTypeInfo> allowedTicketTypes) {
        StringBuilder prompt = new StringBuilder("Allowed ticket types:\n");
        for (TriageRequest.TicketTypeInfo typeInfo : allowedTicketTypes) {
            prompt.append("- ").append(typeInfo.type()).append(": ").append(typeInfo.description()).append("\n");
        }
        return prompt.toString();
    }
}