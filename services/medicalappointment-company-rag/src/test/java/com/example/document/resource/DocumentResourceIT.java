package com.example.document.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DocumentResourceIT {

    @Test
    void upsert() {
        var body = """
                  {"documentName":"DocA","content":"hello","rbacTeams":["team1","team2"]}
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/documents/upsert")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"));

    }

    @Test
    void search() {

        var body = """
                  {"originalText":"test query",
                  "maxResults":5,"minScore":0.5}
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/documents/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(1));
    }

    @Test
    void deleteByName() {
        given()
                .when()
                .delete("/api/documents/{documentName}", "DocA")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"));

    }

    @Test
    void deleteLegacyBody() {
        var body = """
                  {"documentName":"DocB"}
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .delete("/api/documents/delete")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"));

    }

    @Test
    void getAllDocuments() {

        given()
                .when()
                .get("/api/documents/all")
                .then()
                .statusCode(200)
                .body("documents.size()", greaterThan(1));
    }

    @Test
    void getDocumentContent() {
        given()
                .when()
                .get("/api/documents/content/{documentName}", "Payment_System_Payment_Flow.txt")
                .then()
                .statusCode(200)
                .body("content", not(blankOrNullString()));
    }

    @Test
    void getDocumentContentReturns500() {
        given()
                .when()
                .get("/api/documents/content/{documentName}", "__does_not_exist__.txt")
                .then()
                .statusCode(500);
    }

    @Test
    void getAllChunks() {

        given()
                .when()
                .get("/api/documents/chunks")
                .then()
                .statusCode(200)
                .body("chunks.size()", greaterThan(1))
                .body("chunks[0].chunkIndex", equalTo(0))
                .body("chunks[0].vector.size()", is(3072));
    }
}
