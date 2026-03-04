package com.example.appointment.service;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class AiService {

    private static final Logger LOG = Logger.getLogger(AiService.class);

    @Inject
    AiTriageAssistant aiTriageAssistant;

    public AiTriageResult triage(String userMessage, List<TriageRequest.TicketTypeInfo> allowedTicketTypes) {
        try {
            AiTriageResult result = aiTriageAssistant.classify(userMessage, allowedTicketTypes);
            return clampResponse(result);
        } catch (Exception e) {
            LOG.errorf(e, "Error during AI triage");
            throw new RuntimeException("AI_TRIAGE_FAILED: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        }
    }

    private AiTriageResult clampResponse(AiTriageResult result) {
        if (result == null) {
            throw new RuntimeException("PARSE_FAILED: AI response was empty");
        }
        Integer clampedUrgency = result.urgencyScore() == null
                ? null
                : Math.max(1, Math.min(10, result.urgencyScore()));
        Integer clampedConfidence = result.aiConfidencePercent() == null
                ? null
                : Math.max(0, Math.min(100, result.aiConfidencePercent()));

        return new AiTriageResult(result.ticketType(), clampedUrgency, clampedConfidence);
    }
}