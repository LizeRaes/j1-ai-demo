package com.example.urgency.validation;

import java.util.Objects;

public final class RequiredText {

    private static final String LABEL_LABEL = "label";
    private static final String REQUIRED_SUFFIX = " is required";

    private final String label;

    public RequiredText(String label) {
        this.label = Objects.requireNonNull(label, LABEL_LABEL);
    }

    public String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + REQUIRED_SUFFIX);
        }
        return value.strip();
    }
}
