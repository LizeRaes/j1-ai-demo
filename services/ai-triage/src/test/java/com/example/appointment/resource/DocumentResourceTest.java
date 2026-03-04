package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class DocumentResourceTest {

    @Test
    void proxyDocumentContent() {
        given()
        .when()
                .get("/api/documents/content/doc.md")
        .then()
                .statusCode(anyOf(equalTo(200), equalTo(503)));
    }

    @Test
    void proxyDocumentDownload() {
        given()
        .when()
                .get("/api/documents/download/doc.pdf")
        .then()
                .statusCode(anyOf(equalTo(200), equalTo(503)));
    }
}