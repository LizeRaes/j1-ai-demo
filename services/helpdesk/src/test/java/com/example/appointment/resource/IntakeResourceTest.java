package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class IntakeResourceTest {

    @Test
    void createIncomingRequestReturnsBadRequestWhenUserIdMissing() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "userId": " ",
                          "message": "hello"
                        }
                        """)
                .when()
                .post("/api/intake/incoming-request")
                .then()
                .statusCode(400)
                .body(equalTo("userId is required"));
    }

    @Test
    void createIncomingRequestReturnsCreatedWhenValidPayload() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "userId": "intake-user",
                          "message": "Need help with appointment"
                        }
                        """)
                .when()
                .post("/api/intake/incoming-request")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("userId", equalTo("intake-user"));
    }
}