package com.example.urgency;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeepNettsSerializationFilterConfigurationTest {

    private static final List<String> PROPERTIES = List.of(
            DeepNettsSerializationFilterConfiguration.JDK_SERIAL_FILTER_PROPERTY,
            DeepNettsSerializationFilterConfiguration.HELIDON_SERIAL_FILTER_PATTERN_PROPERTY,
            DeepNettsSerializationFilterConfiguration.HELIDON_MISSING_SERIAL_FILTER_ACTION_PROPERTY
    );

    @AfterEach
    void clearProperties() {
        PROPERTIES.forEach(System::clearProperty);
    }

    @Test
    void configuresHelidonFilterWhenNoSerialFilterExists() {
        DeepNettsSerializationFilterConfiguration.configureRuntime();

        assertEquals(
                DeepNettsSerializationFilterConfiguration.DEEPNETTS_MODEL_FILTER_PATTERN,
                System.getProperty(DeepNettsSerializationFilterConfiguration.HELIDON_SERIAL_FILTER_PATTERN_PROPERTY)
        );
        assertEquals(
                DeepNettsSerializationFilterConfiguration.CONFIGURE_ACTION,
                System.getProperty(DeepNettsSerializationFilterConfiguration.HELIDON_MISSING_SERIAL_FILTER_ACTION_PROPERTY)
        );
    }

    @Test
    void keepsUserProvidedJdkSerialFilter() {
        System.setProperty(DeepNettsSerializationFilterConfiguration.JDK_SERIAL_FILTER_PROPERTY, "java.base/*;!*");

        DeepNettsSerializationFilterConfiguration.configureRuntime();

        assertEquals("java.base/*;!*", System.getProperty(DeepNettsSerializationFilterConfiguration.JDK_SERIAL_FILTER_PROPERTY));
        assertNull(System.getProperty(DeepNettsSerializationFilterConfiguration.HELIDON_SERIAL_FILTER_PATTERN_PROPERTY));
        assertNull(System.getProperty(DeepNettsSerializationFilterConfiguration.HELIDON_MISSING_SERIAL_FILTER_ACTION_PROPERTY));
    }

    @Test
    void keepsUserProvidedHelidonPattern() {
        System.setProperty(DeepNettsSerializationFilterConfiguration.HELIDON_SERIAL_FILTER_PATTERN_PROPERTY, "java.base/*;!*");

        DeepNettsSerializationFilterConfiguration.configureRuntime();

        assertEquals("java.base/*;!*", System.getProperty(DeepNettsSerializationFilterConfiguration.HELIDON_SERIAL_FILTER_PATTERN_PROPERTY));
        assertEquals(
                DeepNettsSerializationFilterConfiguration.CONFIGURE_ACTION,
                System.getProperty(DeepNettsSerializationFilterConfiguration.HELIDON_MISSING_SERIAL_FILTER_ACTION_PROPERTY)
        );
    }
}
