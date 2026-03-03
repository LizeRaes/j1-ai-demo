package com.example.appointment.resource;

import com.example.appointment.external.HelpdeskClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
public class HelpResourceTest {

    @InjectMock
    @RestClient
    HelpdeskClient helpdeskClient;

    @Test
    void givenHelpEndpointThenRenderHelpPage() {
        given()
                .when().get("/help")
                .then()
                .statusCode(200)
                .body(containsString("Help / Support"));
    }

    @Test
    void givenHelpFormSubmissionThenRedirectWithSubmittedFlag() {
        when(helpdeskClient.submitRequest(any()))
                .thenReturn(Response.status(Response.Status.CREATED).build());

        given()
                .contentType("application/x-www-form-urlencoded")
                .redirects().follow(false)
                .formParam("message", "Need help with appointment")
                .when().post("/help")
                .then()
                .statusCode(303)
                .header("Location", containsString("/help?submitted=true"));
    }

    @Test
    void givenHelpFormSubmissionThenRedirectWithErrorFlag() {
        when(helpdeskClient.submitRequest(any()))
                .thenReturn(Response.status(Response.Status.BAD_REQUEST)
                        .entity("Invalid request")
                        .build());

        given()
                .contentType("application/x-www-form-urlencoded")
                .redirects().follow(false)
                .formParam("message", "Need help with appointment")
                .when().post("/help")
                .then()
                .statusCode(303)
                .header("Location", containsString("/help?error="));
    }
}
