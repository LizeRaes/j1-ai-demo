package com.example.appointment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.TriageRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class AiService {

    private static final Logger LOG = Logger.getLogger(AiService.class);

    @ConfigProperty(name = "ai-triage.model.name", defaultValue = "gpt-4o-mini")
    String modelName;

    @ConfigProperty(name = "ai-triage.model.temperature", defaultValue = "0.2")
    Double temperature;

    @ConfigProperty(name = "ai-triage.model.timeout", defaultValue = "15S")
    Duration timeout;

    @Inject
    ObjectMapper objectMapper;

    private String systemPrompt;
    private ChatLanguageModel chatModel;

    @PostConstruct
    public void init() {
        try {
            systemPrompt = loadSystemPrompt();
            chatModel = createChatModel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize AiService", e);
        }
    }

    public AiTriageResult triage(String userMessage, List<TriageRequest.TicketTypeInfo> allowedTicketTypes) {
        if (chatModel == null) {
            init();
        }

        String userPrompt = buildUserPrompt(userMessage, allowedTicketTypes);
        
        try {
            CompletableFuture<Response<AiMessage>> future = CompletableFuture.supplyAsync(() -> {
                SystemMessage systemMsg = new SystemMessage(systemPrompt);
                UserMessage userMsg = new UserMessage(userPrompt);
                return chatModel.generate(systemMsg, userMsg);
            });

            Response<AiMessage> response = future.get(timeout.toSeconds(), TimeUnit.SECONDS);
            String responseText = response.content().text();
            return parseResponse(responseText);
        } catch (TimeoutException e) {
            LOG.errorf("AI service timeout after %s", timeout);
            throw new RuntimeException("SERVICE_TIMEOUT: AI service exceeded timeout of " + timeout, e);
        } catch (Exception e) {
            LOG.errorf(e, "Error during AI triage");
            throw new RuntimeException("AI_TRIAGE_FAILED: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        }
    }

    private String buildUserPrompt(String userMessage, List<TriageRequest.TicketTypeInfo> allowedTicketTypes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Customer request: ").append(userMessage).append("\n\n");
        prompt.append("Allowed ticket types:\n");
        
        for (TriageRequest.TicketTypeInfo typeInfo : allowedTicketTypes) {
            prompt.append("- ").append(typeInfo.getType())
                  .append(": ").append(typeInfo.getDescription()).append("\n");
        }
        
        prompt.append("\nClassify this request and return the JSON response as specified.");
        
        return prompt.toString();
    }

    private AiTriageResult parseResponse(String response) {
        try {
            // Try to extract JSON from the response (in case LLM adds extra text)
            String json = extractJson(response);
            AiTriageResult result = objectMapper.readValue(json, AiTriageResult.class);
            
            // Clamp values
            if (result.getUrgencyScore() != null) {
                result.setUrgencyScore(Math.max(1, Math.min(10, result.getUrgencyScore())));
            }
            if (result.getAiConfidencePercent() != null) {
                result.setAiConfidencePercent(Math.max(0, Math.min(100, result.getAiConfidencePercent())));
            }
            
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse AI response: %s", response);
            throw new RuntimeException("PARSE_FAILED: " + e.getMessage(), e);
        }
    }

    private String extractJson(String response) {
        // Try to find JSON object in the response
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return response.trim();
    }

    private ChatLanguageModel createChatModel() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("OPENAI_API_KEY environment variable is not set");
        }

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();
    }

    private String loadSystemPrompt() throws IOException {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("prompts/system-prompt.txt")) {
            if (is == null) {
                throw new IOException("System prompt file not found: prompts/system-prompt.txt");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
