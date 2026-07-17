package com.example.urgency;

final class DeepNettsSerializationFilterConfiguration {

    static final String JDK_SERIAL_FILTER_PROPERTY = "jdk.serialFilter";
    static final String HELIDON_SERIAL_FILTER_PATTERN_PROPERTY = "helidon.serialFilter.pattern";
    static final String HELIDON_MISSING_SERIAL_FILTER_ACTION_PROPERTY = "helidon.serialFilter.missing.action";
    static final String CONFIGURE_ACTION = "configure";
    static final String DEEPNETTS_MODEL_FILTER_PATTERN = "deepnetts.**;javax.visrec.**;java.base/*;!*";

    private DeepNettsSerializationFilterConfiguration() {
    }

    static void configureRuntime() {
        if (hasText(System.getProperty(JDK_SERIAL_FILTER_PROPERTY))) {
            return;
        }
        if (!hasText(System.getProperty(HELIDON_SERIAL_FILTER_PATTERN_PROPERTY))) {
            System.setProperty(HELIDON_SERIAL_FILTER_PATTERN_PROPERTY, DEEPNETTS_MODEL_FILTER_PATTERN);
        }
        if (!hasText(System.getProperty(HELIDON_MISSING_SERIAL_FILTER_ACTION_PROPERTY))) {
            System.setProperty(HELIDON_MISSING_SERIAL_FILTER_ACTION_PROPERTY, CONFIGURE_ACTION);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
