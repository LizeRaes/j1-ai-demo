package com.example.aicodingassistant.validation;

import com.example.aicodingassistant.domain.JobSubmissionStatus;
import com.example.aicodingassistant.dto.SubmitJobRequest;
import com.example.aicodingassistant.dto.SubmitJobResponse;
import jakarta.ws.rs.core.Response;

public final class RequestValidation {
    private RequestValidation() {
    }

    public static Response validate(SubmitJobRequest request) {
        if (request == null) {
            return bad("Request body is required.");
        }
        if (request.ticketId() == null || request.ticketId() <= 0) {
            return bad("ticketId must be a positive number.");
        }
        if (isBlank(request.originalRequest())) {
            return bad("originalRequest is required.");
        }
        if (isBlank(request.repoUrl())) {
            return bad("repoUrl is required.");
        }
        if (request.confidenceThreshold() == null) {
            return bad("confidenceThreshold is required.");
        }
        if (request.confidenceThreshold() < 0 || request.confidenceThreshold() > 1) {
            return bad("confidenceThreshold must be between 0 and 1.");
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Response bad(String message) {
        SubmitJobResponse body = new SubmitJobResponse("", JobSubmissionStatus.REJECTED, message);
        return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
    }
}
