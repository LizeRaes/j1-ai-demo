package com.example.appointment.resource;

import com.example.appointment.dto.AiTriageResult;
import com.example.appointment.dto.DocumentSearchResponse;
import com.example.appointment.dto.SimilaritySearchResponse;
import com.example.appointment.service.DocumentService;
import com.example.appointment.service.SimilarityService;
import com.example.appointment.service.UrgencyService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class TriageResourceTest {

    @InjectMock
    UrgencyService urgencyService;

    @InjectMock
    SimilarityService similarityService;

    @InjectMock
    DocumentService documentService;

    @BeforeEach
    void setUp() {
        when(urgencyService.score(any())).thenReturn(5.0);
        when(similarityService.searchSimilarTickets(any(), any(), any()))
                .thenReturn(new SimilaritySearchResponse(List.of(42L)));
        when(documentService.searchDocuments(any()))
                .thenReturn(new DocumentSearchResponse(List.of()));
    }

    @Test
    void classify() {
        String requestBody = """
                {
                  "incomingRequestId": 1001,
                  "message": "Need refund for failed appointment",
                  "allowedTicketTypes": [
                    {"type": "billing", "description": "Billing issues"}
                  ],
                  "ticketId": 42
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
        .when()
                .post("/api/triage/v1/classify")
        .then()
                .statusCode(200)
                .body("status", notNullValue())
                .body("relatedTicketIds", notNullValue())
                .body("policyCitations", notNullValue());
    }

    @Test
    void getEvents() {
        given()
        .when()
                .get("/api/triage/v1/events")
        .then()
                .statusCode(200);
    }

    @Test
    void getTickets() {
        given()
        .when()
                .get("/api/triage/v1/tickets")
        .then()
                .statusCode(200);
    }
}
