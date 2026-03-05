package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class TriageWorkerResourceTest {

    @Test
    void processNewRequestsReturnsOk() {
        given()
                .when()
                .post("/api/triage-worker/process")
                .then()
                .statusCode(200);
    }
}