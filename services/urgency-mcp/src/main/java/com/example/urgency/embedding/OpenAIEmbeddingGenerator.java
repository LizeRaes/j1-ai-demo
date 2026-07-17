package com.example.urgency.embedding;

import java.util.Objects;
import java.util.Optional;

import com.example.urgency.config.RuntimeConfig;
import com.example.urgency.validation.RequiredText;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.helidon.config.Config;

public final class OpenAIEmbeddingGenerator implements EmbeddingGenerator {

    public static final String PROVIDER_ID = "openai";
    public static final String API_KEY_PROPERTY = "openai.api-key";
    public static final String API_KEY_ENVIRONMENT_VARIABLE = "OPENAI_API_KEY";
    public static final String MODEL_NAME_KEY = "urgency.providers.openai.embedding.model.name";
    public static final String DIMENSIONS_KEY = "urgency.providers.openai.embedding.dimensions";

    private static final String DEFAULT_MODEL_NAME = "text-embedding-3-small";
    private static final String MODEL_LABEL = "OpenAI embedding model";
    private static final String MODEL_NAME_LABEL = "OpenAI embedding model name";
    private static final String TEXT_LABEL = "text";
    private static final String POSITIVE_SUFFIX = " must be positive";
    private static final String MISSING_API_KEY_MESSAGE =
            "OpenAI embeddings require API key. Set 'openai.api-key' or OPENAI_API_KEY.";

    private final EmbeddingModel model;

    public OpenAIEmbeddingGenerator(Config config) {
        this(Settings.fromConfig(config));
    }

    public OpenAIEmbeddingGenerator(Settings settings) {
        this(createModel(settings));
    }

    OpenAIEmbeddingGenerator(EmbeddingModel model) {
        this.model = Objects.requireNonNull(model, MODEL_LABEL);
    }

    @Override
    public float[] embed(String text) {
        String requiredText = new RequiredText(TEXT_LABEL).require(text);
        Embedding embedding = model.embed(requiredText).content();
        return embedding.vector();
    }

    public static Optional<String> resolveApiKey(Config config) {
        RuntimeConfig runtimeConfig = new RuntimeConfig(config);
        return runtimeConfig.optionalText(API_KEY_PROPERTY)
                .or(() -> Optional.ofNullable(System.getenv(API_KEY_ENVIRONMENT_VARIABLE))
                        .filter(value -> !value.isBlank()));
    }

    private static EmbeddingModel createModel(Settings settings) {
        var builder = OpenAiEmbeddingModel.builder()
                .apiKey(settings.apiKey())
                .modelName(settings.modelName());

        settings.dimensions().ifPresent(builder::dimensions);
        return builder.build();
    }

    public record Settings(String modelName, Optional<Integer> dimensions, String apiKey) {
        public Settings {
            modelName = new RequiredText(MODEL_NAME_LABEL).require(modelName);
            dimensions = Objects.requireNonNull(dimensions, DIMENSIONS_KEY);
            apiKey = new RequiredText(API_KEY_PROPERTY).require(apiKey);
            dimensions.ifPresent(value -> {
                if (value < 1) {
                    throw new IllegalArgumentException(DIMENSIONS_KEY + POSITIVE_SUFFIX);
                }
            });
        }

        static Settings fromConfig(Config config) {
            RuntimeConfig runtimeConfig = new RuntimeConfig(config);
            String apiKey = resolveApiKey(config).orElseThrow(() -> new IllegalStateException(MISSING_API_KEY_MESSAGE));
            return new Settings(
                    runtimeConfig.text(MODEL_NAME_KEY, DEFAULT_MODEL_NAME),
                    runtimeConfig.optionalInteger(DIMENSIONS_KEY),
                    apiKey);
        }
    }
}
