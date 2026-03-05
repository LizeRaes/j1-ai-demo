package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class IncomingRequestResourceTest {

    @Test
    void createIncomingRequestReturnsCreated() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "userId": "incoming-user",
                          "channel": "api",
                          "rawText": "Incoming request message"
                        }
                        """)
                .when()
                .post("/api/incoming-requests")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("userId", equalTo("incoming-user"));
    }

    @Test
    void getIncomingRequestReturnsNotFoundForUnknownId() {
        given()
                .when()
                .get("/api/incoming-requests/999999")
                .then()
                .statusCode(404);
    }

    @Test
    void getIncomingRequestsReturnsList() {
        given()
                .when()
                .get("/api/incoming-requests")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
    }
}