package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CodingAssistantCallbackResourceTest {

    @Test
    void receiveCallbackReturnsUnauthorizedWhenTokenIsInvalid() {
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer wrong-token")
                .body("""
                        {
                          "ticketId": 7,
                          "prUrl": "https://github.com/org/repo/pull/1"
                        }
                        """)
                .when()
                .post("/api/coding-assistant")
                .then()
                .statusCode(401)
                .body("status", equalTo("UNAUTHORIZED"));
    }

    @Test
    void receiveCallbackReturnsOkWhenAuthorized() {
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer change-me")
                .body("""
                        {
                          "ticketId": 9,
                          "prUrl": "https://github.com/org/repo/pull/2"
                        }
                        """)
                .when()
                .post("/api/coding-assistant")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"));
    }
}