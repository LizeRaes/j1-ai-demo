package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class DocumentResourceTest {

    @Test
    void getDocumentContentReturnsServiceUnavailableWhenUpstreamUnavailable() {
        given()
                .when()
                .get("/api/documents/content/doc.txt")
                .then()
                .statusCode(503);
    }

    @Test
    void downloadDocumentReturnsServiceUnavailableWhenUpstreamUnavailable() {
        given()
                .when()
                .get("/api/documents/download/doc.txt")
                .then()
                .statusCode(503);
    }
}