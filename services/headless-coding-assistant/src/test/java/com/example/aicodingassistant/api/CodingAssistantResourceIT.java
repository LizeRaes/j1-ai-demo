package com.example.aicodingassistant.api;

import com.example.aicodingassistant.domain.JobSubmissionStatus;
import com.example.aicodingassistant.dto.SubmitJobResponse;
import com.example.aicodingassistant.service.JobOrchestratorService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class CodingAssistantResourceIT {

    @InjectMock
    JobOrchestratorService orchestratorService;

    @Test
    void submitJobAccepted() {
        when(orchestratorService.submit(any()))
                .thenReturn(new SubmitJobResponse("42", JobSubmissionStatus.ACCEPTED, "queued"));

        String body = """
                {
                  "ticketId": 123,
                  "originalRequest": "Confirm appointment leads to error page.",
                  "repoUrl": "https://github.com/LizeRaes/mediflow-user-facing",
                  "confidenceThreshold": 0.6
                }
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/coding-assistant/jobs")
                .then()
                .statusCode(202)
                .body("jobId", equalTo("42"))
                .body("status", equalTo("ACCEPTED"))
                .body("message", equalTo("queued"));

        verify(orchestratorService).submit(any());
    }

    @Test
    void submitJobRejectedByValidation() {
        String body = """
                {
                  "ticketId": 0,
                  "originalRequest": "bad request",
                  "repoUrl": "https://github.com/LizeRaes/mediflow-user-facing",
                  "confidenceThreshold": 0.6
                }
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/coding-assistant/jobs")
                .then()
                .statusCode(400)
                .body("status", equalTo("REJECTED"))
                .body("message", equalTo("ticketId must be a positive number."));
    }

    @Test
    void submitJobRejectedWhenMissingField() {
        String body = """
                {
                  "ticketId": 123,
                  "repoUrl": "https://github.com/LizeRaes/mediflow-user-facing",
                  "confidenceThreshold": 0.6
                }
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/coding-assistant/jobs")
                .then()
                .statusCode(400)
                .body("status", equalTo("REJECTED"))
                .body("message", equalTo("originalRequest is required."));
    }
}
