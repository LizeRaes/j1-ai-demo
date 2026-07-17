package com.example.urgency.config;

import io.helidon.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigTest {

    private static final String TEXT_KEY = "test.runtime.text";
    private static final String INTEGER_KEY = "test.runtime.integer";
    private static final String DECIMAL_KEY = "test.runtime.decimal";
    private static final String MISSING_KEY = "test.runtime.missing";

    @AfterEach
    void clearProperties() {
        System.clearProperty(TEXT_KEY);
        System.clearProperty(INTEGER_KEY);
        System.clearProperty(DECIMAL_KEY);
        System.clearProperty(MISSING_KEY);
    }

    @Test
    void readsSystemPropertyTextBeforeConfig() {
        System.setProperty(TEXT_KEY, " configured value ");
        RuntimeConfig config = new RuntimeConfig(Config.create());

        assertEquals(" configured value ", config.optionalText(TEXT_KEY).orElseThrow());
        assertEquals("configured value", config.requiredText(TEXT_KEY, "test value"));
    }

    @Test
    void readsNumbersFromSystemProperties() {
        System.setProperty(INTEGER_KEY, "42");
        System.setProperty(DECIMAL_KEY, "0.25");
        RuntimeConfig config = new RuntimeConfig(Config.create());

        assertEquals(42, config.integer(INTEGER_KEY));
        assertEquals(42, config.optionalInteger(INTEGER_KEY).orElseThrow());
        assertEquals(0.25, config.decimal(DECIMAL_KEY));
    }

    @Test
    void returnsDefaultForMissingText() {
        RuntimeConfig config = new RuntimeConfig(Config.create());

        assertEquals("fallback", config.text(MISSING_KEY, "fallback"));
        assertTrue(config.optionalText(MISSING_KEY).isEmpty());
    }

    @Test
    void rejectsMissingRequiredText() {
        RuntimeConfig config = new RuntimeConfig(Config.create());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.requiredText(MISSING_KEY, "missing value")
        );

        assertEquals("missing value is required", exception.getMessage());
    }
}
