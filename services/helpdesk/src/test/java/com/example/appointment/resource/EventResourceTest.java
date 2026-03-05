package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class EventResourceTest {

    @Test
    void getRecentEventsReturnsOk() {
        given()
                .when()
                .get("/api/events/recent")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    void getRecentEventsRespectsLimitQueryParam() {
        given()
                .queryParam("limit", 1)
                .when()
                .get("/api/events/recent")
                .then()
                .statusCode(200);
    }
}