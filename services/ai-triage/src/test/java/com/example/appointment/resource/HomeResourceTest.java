package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class HomeResourceTest {

    @Test
    void index() {
        given()
        .when()
                .get("/")
        .then()
                .statusCode(200)
                .body(containsString("<html"));
    }
}