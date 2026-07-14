package com.example.urgency.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequiredTextTest {

    @Test
    void stripsValidText() {
        assertEquals("value", new RequiredText("field").require(" value "));
    }

    @Test
    void rejectsMissingText() {
        RequiredText field = new RequiredText("field");

        assertThrows(IllegalArgumentException.class, () -> field.require(null));
        assertThrows(IllegalArgumentException.class, () -> field.require(" "));
    }
}
