package com.example.document.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AccessResourceIT {

    @Test
    void updateWithTeams() {
        var body = """
                    {"documentName":"DocA","rbacTeams":["team1","team2"]}
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/rbac/update")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"));

    }

    @Test
    void updateWithoutTeams() {
        var body = """
                    {"documentName":"DocB"}
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/rbac/update")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"));

    }

    @Test
    void updateMissingDocumentName() {
        var body = """
                    {"rbacTeams":["team1"]}
                """;

        given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/rbac/update")
                .then()
                .statusCode(500);

    }
}