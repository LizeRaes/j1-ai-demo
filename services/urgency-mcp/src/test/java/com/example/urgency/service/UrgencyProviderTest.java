package com.example.urgency.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrgencyProviderTest {

    @Test
    void parsesSupportedProvidersCaseInsensitively() {
        assertEquals(UrgencyProvider.LOCAL, UrgencyProvider.parse("LOCAL"));
        assertEquals(UrgencyProvider.OPENAI, UrgencyProvider.parse("openai"));
    }

    @Test
    void rejectsUnsupportedProvider() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> UrgencyProvider.parse("remote")
        );

        assertEquals("Unsupported urgency.provider: remote", exception.getMessage());
    }
}
