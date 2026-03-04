package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.contains;

@QuarkusTest
public class AccountResourceTest {

    @Test
    void givenAccountEndpointThenRenderAccountPage() {
        given()
                .when().get("/account")
                .then()
                .statusCode(200)
                .body(containsString("Account"));
    }

    @Test
    void givenResetPasswordEndpointThenRedirectBackToAccount() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .redirects().follow(false)
                .when().post("/account/reset-password")
                .then()
                .statusCode(303)
                .header("Location", containsString("/account?"));
    }
}
