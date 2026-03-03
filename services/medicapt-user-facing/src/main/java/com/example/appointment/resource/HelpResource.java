package com.example.appointment.resource;

import com.example.appointment.model.IntakeRequest;
import com.example.appointment.services.AccountService;
import com.example.appointment.external.HelpdeskClient;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.net.URI;
import java.net.URLEncoder;

@Path("/help")
public class HelpResource {
    @Inject
    Template help;

    @Inject
    AccountService accountService;

    @RestClient
    HelpdeskClient helpdeskClient;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String help(@QueryParam("submitted") String submitted, @QueryParam("error") String error) {
        return help.data("submitted", submitted != null)
                .data("error", error)
                .render();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response submitHelpForm(@FormParam("message") String message) {
        String userId = accountService.getUserId();
        IntakeRequest request = new IntakeRequest(userId,  message);

        try (Response response = helpdeskClient.submitRequest(request)) {

            if (response.getStatusInfo().equals(Response.Status.CREATED)) {
                // Success - no error message
                return Response.seeOther(URI.create("/help?submitted=true"))
                        .build();
            } else {
                // Failure - pass error message as URL parameter (URL encoded)
                String encodedError = URLEncoder.encode(response.readEntity(String.class), java.nio.charset.StandardCharsets.UTF_8);
                return Response.seeOther(URI.create("/help?error=" + encodedError))
                        .build();
            }
        }
    }
}
