package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class BillingResourceTest {

    @Test
    void givenPaymentEndpointThenRenderPaymentPage() {
        given()
                .when().get("/billing/pay")
                .then()
                .statusCode(200)
                .body(containsString("Payment"));
    }

    @Test
    void givenPaymentFormThenRedirectToSuccessPage() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .redirects().follow(false)
                .formParam("appointmentId", "apt-123")
                .when().post("/billing/pay")
                .then()
                .statusCode(303)
                .header("Location", containsString("/billing/pay/success"));
    }

    @Test
    void givenSuccessEndpointThenRenderSuccessPage() {
        given()
                .when().get("/billing/pay/success")
                .then()
                .statusCode(200);
    }
}
