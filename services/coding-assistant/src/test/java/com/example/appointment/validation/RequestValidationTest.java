package com.example.appointment.validation;

import com.example.appointment.dto.SubmitJobRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestValidationTest {

    @Test
    void rejectNullRequest() {
        Response response = RequestValidation.validate(null);
        assertNotNull(response);
        assertEquals(400, response.getStatus());
    }

    @Test
    void rejectInvalidTicketId() {
        SubmitJobRequest request = new SubmitJobRequest(0L, "bug", "https://repo", 0.6);
        Response response = RequestValidation.validate(request);
        assertNotNull(response);
        assertEquals(400, response.getStatus());
    }

    @Test
    void rejectInvalidConfidenceRange() {
        SubmitJobRequest request = new SubmitJobRequest(10L, "bug", "https://repo", 1.5);
        Response response = RequestValidation.validate(request);
        assertNotNull(response);
        assertEquals(400, response.getStatus());
    }

    @Test
    void acceptValidRequest() {
        SubmitJobRequest request = new SubmitJobRequest(123L, "bug report", "https://repo", 0.6);
        Response response = RequestValidation.validate(request);
        assertNull(response);
    }
}
