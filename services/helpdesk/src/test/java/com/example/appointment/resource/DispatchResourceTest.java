package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class DispatchResourceTest {

    @Test
    void submitTicketReturnsBadRequestWhenIncomingRequestDoesNotExist() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "incomingRequestId": 999999,
                          "ticketType": "OTHER",
                          "urgencyFlag": false,
                          "urgencyScore": 3.0,
                          "dispatcherId": "dispatcher",
                          "notes": "note"
                        }
                        """)
                .when()
                .post("/api/dispatch/submit-ticket")
                .then()
                .statusCode(400);
    }

    @Test
    void submitTicketReturnsCreatedWhenIncomingRequestExists() {
        Long requestId =
                given()
                        .contentType("application/json")
                        .body("""
                                {
                                  "userId": "dispatch-user",
                                  "channel": "api",
                                  "rawText": "Need support"
                                }
                                """)
                        .when()
                        .post("/api/incoming-requests")
                        .then()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getLong("id");

        given()
                .contentType("application/json")
                .body("""
                        {
                          "incomingRequestId": %d,
                          "ticketType": "OTHER",
                          "urgencyFlag": false,
                          "urgencyScore": 3.0,
                          "dispatcherId": "dispatcher",
                          "notes": "note"
                        }
                        """.formatted(requestId))
                .when()
                .post("/api/dispatch/submit-ticket")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("incomingRequestId", equalTo(requestId.intValue()));
    }
}