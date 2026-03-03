package com.example.appointment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
public class AppointmentResourceTest {

    @Test
    void givenBookEndpointThenRenderBookPage() {
        given()
                .when().get("/appointments/book")
                .then()
                .statusCode(200)
                .body(containsString("Book Appointment"));
    }

    @Test
    void givenValidAppointmentFormThenRedirectToBilling() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .redirects().follow(false)
                .formParam("doctor", "Dr. Alice")
                .formParam("date", "today")
                .formParam("time", "09:00")
                .when().post("/appointments/book")
                .then()
                .statusCode(303)
                .header("Location", containsString("/billing/pay?appointmentId="));
    }

    @Test
    void givenAppointmentsEndpointThenRenderAppointmentsPage() {
        given()
                .when().get("/appointments")
                .then()
                .statusCode(200)
                .body(containsString("My Appointments"));
    }
}
