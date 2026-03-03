package com.example.appointment.service;

import com.example.appointment.domain.AnalysisResult;
import com.example.appointment.domain.FixResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@ApplicationScoped
final class AiCodingAssistantRunner {
    private static final int LIMIT = 100;

    private final CommandRunner commandRunner;
    private final ObjectMapper objectMapper;

    @Inject
    AiCodingAssistantRunner(CommandRunner commandRunner, ObjectMapper objectMapper) {
        this.commandRunner = commandRunner;
        this.objectMapper = objectMapper;
    }

    AnalysisResult runAnalyze(Path repoDir, long ticketId, String bugReport, String model, Consumer<String> logger)
            throws IOException, InterruptedException {
        String prompt = "Analyze the bug report against this repository.\n"
                + "Ticket ID: " + ticketId + "\n"
                + "Bug report:\n"
                + bugReport + "\n\n"
                + "Return STRICT JSON only with this shape:\n"
                + "{\n"
                + "  \"likelyCause\": \"short explanation including helpful file paths and line numbers inline when available\",\n"
                + "  \"confidence\": 0.0\n"
                + "}\n"
                + "confidence must be a decimal between 0 and 1.";
        String payload = runAssistantAndGetAgentMessage(repoDir, prompt, model, logger);
        return objectMapper.readValue(payload, AnalysisResult.class);
    }

    FixResult runFix(
            Path repoDir,
            long ticketId,
            String bugReport,
            AnalysisResult analyzeResult,
            String model,
            Consumer<String> logger)
            throws IOException, InterruptedException {
        String likelyCause = analyzeResult.likelyCause();
        if (likelyCause == null || likelyCause.isBlank()) {
            throw new IllegalStateException("AI likelyCause must be non-empty before fix.");
        }
        String prompt = "Given this bug report and suspected cause, apply a fix in code.\n"
                + "Ticket ID: " + ticketId + "\n"
                + "Bug report:\n"
                + bugReport + "\n\n"
                + "Suspected cause:\n"
                + "likelyCause=" + likelyCause + "\n\n"
                + "IMPORTANT RULES:\n"
                + "- Do NOT run git commands.\n"
                + "- Do NOT create branches.\n"
                + "- Only modify files needed for the fix.\n"
                + "- Add or update tests if appropriate.\n\n"
                + "Return STRICT JSON only with this shape:\n"
                + "{\n"
                + "  \"fixSummary\": \"short summary of what was changed\"\n"
                + "}";

        String payload = runAssistantAndGetAgentMessage(repoDir, prompt, model, logger);
        return objectMapper.readValue(payload, FixResult.class);
    }

    private String runAssistantAndGetAgentMessage(Path workingDir, String prompt, String model, Consumer<String> logger)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("codex");
        cmd.add("exec");
        cmd.add("--json");
        cmd.add("--full-auto");
        if (model != null && !model.isBlank()) {
            cmd.add("--model");
            cmd.add(model);
        }
        cmd.add(prompt);
        logger.accept("[ai-coding-assistant] Executing command: " + String.join(" ", cmd));

        final String[] agentMessage = {null};
        CommandRunner.CommandResult result = commandRunner.run(workingDir, cmd, line -> {
            try {
                JsonNode root = objectMapper.readTree(line);
                String summary = summarizeEvent(root, agentMessage);
                if (summary != null && !summary.isBlank()) {
                    logger.accept("[ai-coding-assistant] " + summary);
                }
            } catch (Exception ignored) {
                logger.accept("[ai-coding-assistant] " + truncate(line));
            }
        });

        if (result.exitCode() != 0) {
            throw new IllegalStateException("AI Coding Assistant command failed with exit code " + result.exitCode());
        }
        if (agentMessage[0] == null || agentMessage[0].isBlank()) {
            throw new IllegalStateException("No agent_message payload found in AI Coding Assistant JSON stream.");
        }
        logger.accept("[ai-coding-assistant] Final agent message: " + agentMessage[0]);
        return agentMessage[0];
    }

    private String summarizeEvent(JsonNode root, String[] agentMessageHolder) {
        String type = root.path("type").asText();
        JsonNode item = root.path("item");
        String itemType = item.path("type").asText();
        if ("agent_message".equals(itemType)) {
            String text = item.path("text").asText();
            if (text != null && !text.isBlank()) {
                agentMessageHolder[0] = text;
            }
        }

        String payload = item.path("text").asText();
        if (payload == null || payload.isBlank()) {
            payload = item.path("command").asText();
        }
        if (payload == null || payload.isBlank()) {
            payload = item.path("aggregated_output").asText();
        }
        if (payload == null || payload.isBlank()) {
            payload = root.path("thread_id").asText();
        }

        String summary = "type=" + truncate(type);
        if (itemType != null && !itemType.isBlank()) {
            summary += ", item.type=" + truncate(itemType);
        }
        if (payload != null && !payload.isBlank()) {
            summary += ", payload=" + truncate(payload);
        }
        return summary;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= LIMIT) {
            return value;
        }
        return value.substring(0, LIMIT) + "...";
    }
}
