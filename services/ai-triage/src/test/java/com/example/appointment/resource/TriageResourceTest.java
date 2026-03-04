package com.example.appointment.resource;

import com.example.appointment.dto.AiTriageResult;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TriageResourceTest {

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