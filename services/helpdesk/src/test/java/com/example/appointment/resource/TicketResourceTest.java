package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TicketResourceTest {

    @Test
    void createTicketManualReturnsCreated() {
        Long ticketId =
                given()
                        .contentType("application/json")
                        .body("""
                                {
                                  "userId": "manual-user",
                                  "originalRequest": "Cannot access account",
                                  "ticketType": "ACCOUNT_ACCESS",
                                  "status": "FROM_DISPATCH",
                                  "assignedTo": "agent-1",
                                  "urgencyFlag": false,
                                  "urgencyScore": 2.0
                                }
                                """)
                        .when()
                        .post("/api/tickets/manual")
                        .then()
                        .statusCode(201)
                        .body("id", notNullValue())
                        .extract()
                        .jsonPath()
                        .getLong("id");

        given()
                .when()
                .get("/api/tickets/{id}", ticketId)
                .then()
                .statusCode(200)
                .body("id", equalTo(ticketId.intValue()));
    }

    @Test
    void getTicketsReturnsOk() {
        given()
                .when()
                .get("/api/tickets")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    void getTicketReturnsNotFoundForUnknownId() {
        given()
                .when()
                .get("/api/tickets/999999")
                .then()
                .statusCode(404);
    }
}