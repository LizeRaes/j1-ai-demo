package com.example.urgency.config;

import java.util.Objects;
import java.util.Optional;

import com.example.urgency.validation.RequiredText;
import io.helidon.config.Config;

public final class RuntimeConfig {

    private static final String CONFIG_LABEL = "config";

    private final Config config;

    public RuntimeConfig(Config config) {
        this.config = Objects.requireNonNull(config, CONFIG_LABEL);
    }

    public String requiredText(String key, String label) {
        return new RequiredText(label).require(optionalText(key).orElse(null));
    }

    public String text(String key, String defaultValue) {
        return optionalText(key).orElse(defaultValue);
    }

    public Optional<String> optionalText(String key) {
        String fromSystemProperty = System.getProperty(key);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return Optional.of(fromSystemProperty);
        }
        return config.get(key)
                .asString()
                .asOptional()
                .filter(value -> !value.isBlank());
    }

    public int integer(String key) {
        String fromSystemProperty = System.getProperty(key);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return Integer.parseInt(fromSystemProperty);
        }
        return config.get(key).asInt().get();
    }

    public Optional<Integer> optionalInteger(String key) {
        String fromSystemProperty = System.getProperty(key);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return Optional.of(Integer.parseInt(fromSystemProperty));
        }
        return config.get(key).asInt().asOptional();
    }

    public double decimal(String key) {
        String fromSystemProperty = System.getProperty(key);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return Double.parseDouble(fromSystemProperty);
        }
        return config.get(key).asDouble().get();
    }
}
