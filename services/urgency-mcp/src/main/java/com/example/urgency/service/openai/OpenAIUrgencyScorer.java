package com.example.urgency.service.openai;

import com.example.urgency.config.RuntimeConfig;
import com.example.urgency.embedding.OpenAIEmbeddingGenerator;
import com.example.urgency.service.UrgencyScorer;
import com.example.urgency.validation.RequiredText;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.helidon.config.Config;

import java.util.Objects;
import java.util.regex.Pattern;

final class OpenAIUrgencyScorer implements UrgencyScorer {

    static final String MODEL_NAME_KEY = "urgency.providers.openai.model.name";
    static final String PROMPT_KEY = "urgency.providers.openai.prompt";
    static final String TEMPERATURE_KEY = "urgency.providers.openai.temperature";
    static final String MAX_COMPLETION_TOKENS_KEY = "urgency.providers.openai.max-completion-tokens";

    private static final String NUMBER_PATTERN_TEXT = "[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)";
    private static final String MODEL_LABEL = "model";
    private static final String MODEL_NAME_LABEL = "OpenAI model name";
    private static final String PROMPT_LABEL = "OpenAI urgency prompt";
    private static final String COMPLAINT_LABEL = "complaint";
    private static final String RESPONSE_LABEL = "OpenAI urgency response";
    private static final String POSITIVE_SUFFIX = " must be positive";
    private static final String MISSING_API_KEY_MESSAGE =
            "OpenAI urgency scoring requires API key. Set 'openai.api-key' or OPENAI_API_KEY.";
    private static final String PROMPT_COMPLAINT_LABEL = "Complaint:";
    private static final String MISSING_NUMERIC_SCORE_PREFIX =
            "OpenAI urgency response did not contain a numeric score: ";
    private static final String NON_FINITE_SCORE_PREFIX = "OpenAI urgency response score was not finite: ";
    private static final Pattern NUMBER_PATTERN = Pattern.compile(NUMBER_PATTERN_TEXT);

    private final ChatModel model;
    private final String prompt;

    OpenAIUrgencyScorer(Config config) {
        this(settings(config), OpenAIEmbeddingGenerator.resolveApiKey(config)
                .orElseThrow(() -> new IllegalStateException(MISSING_API_KEY_MESSAGE)));
    }

    OpenAIUrgencyScorer(Settings settings) {
        this(settings, OpenAIEmbeddingGenerator.resolveApiKey(Config.create())
                .orElseThrow(() -> new IllegalStateException(MISSING_API_KEY_MESSAGE)));
    }

    OpenAIUrgencyScorer(ChatModel model, String prompt) {
        this.model = Objects.requireNonNull(model, MODEL_LABEL);
        this.prompt = new RequiredText(PROMPT_LABEL).require(prompt);
    }

    @Override
    public double score(String complaint) {
        String requiredComplaint = new RequiredText(COMPLAINT_LABEL).require(complaint);

        String response = model.chat(prompt(requiredComplaint));
        return parseScore(response);
    }

    private static Settings settings(Config config) {
        RuntimeConfig runtimeConfig = new RuntimeConfig(config);
        return new Settings(
                runtimeConfig.requiredText(MODEL_NAME_KEY, MODEL_NAME_LABEL),
                runtimeConfig.requiredText(PROMPT_KEY, PROMPT_LABEL),
                runtimeConfig.decimal(TEMPERATURE_KEY),
                runtimeConfig.integer(MAX_COMPLETION_TOKENS_KEY));
    }

    private OpenAIUrgencyScorer(Settings settings, String apiKey) {
        this(createModel(settings, apiKey), settings.prompt());
    }

    private static ChatModel createModel(Settings settings, String apiKey) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(settings.modelName())
                .temperature(settings.temperature())
                .maxCompletionTokens(settings.maxCompletionTokens())
                .build();
    }

    private String prompt(String complaint) {
        return prompt
                + System.lineSeparator()
                + PROMPT_COMPLAINT_LABEL
                + System.lineSeparator()
                + complaint;
    }

    private static double parseScore(String response) {
        String requiredResponse = new RequiredText(RESPONSE_LABEL).require(response);
        var matcher = NUMBER_PATTERN.matcher(requiredResponse);
        if (!matcher.find()) {
            throw new IllegalStateException(MISSING_NUMERIC_SCORE_PREFIX + requiredResponse);
        }

        double score = Double.parseDouble(matcher.group());
        if (!Double.isFinite(score)) {
            throw new IllegalStateException(NON_FINITE_SCORE_PREFIX + requiredResponse);
        }
        return Math.clamp(score, 0.0, 10.0);
    }

    record Settings(String modelName, String prompt, double temperature, int maxCompletionTokens) {
        Settings {
            modelName = new RequiredText(MODEL_NAME_LABEL).require(modelName);
            prompt = new RequiredText(PROMPT_LABEL).require(prompt);
            if (maxCompletionTokens < 1) {
                throw new IllegalArgumentException(MAX_COMPLETION_TOKENS_KEY + POSITIVE_SUFFIX);
            }
        }
    }
}
