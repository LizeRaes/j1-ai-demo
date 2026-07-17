package com.example.urgency.service;

import java.util.Locale;

import com.example.urgency.embedding.OpenAIEmbeddingGenerator;
import com.example.urgency.validation.RequiredText;

enum UrgencyProvider {
    LOCAL("local"),
    OPENAI(OpenAIEmbeddingGenerator.PROVIDER_ID);

    private static final String PROVIDER_LABEL = "provider";
    private static final String UNSUPPORTED_PROVIDER_PREFIX = "Unsupported urgency.provider: ";

    private final String id;

    UrgencyProvider(String id) {
        this.id = id;
    }

    static UrgencyProvider parse(String raw) {
        String provider = new RequiredText(PROVIDER_LABEL).require(raw).toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "local" -> LOCAL;
            case OpenAIEmbeddingGenerator.PROVIDER_ID -> OPENAI;
            default -> throw new IllegalArgumentException(UNSUPPORTED_PROVIDER_PREFIX + raw);
        };
    }

    String id() {
        return id;
    }
}
