package com.example.appointment.external;

import com.example.appointment.model.IntakeRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/intake")
@RegisterRestClient(configKey = "helpdesk-api")
public interface HelpdeskClient {

    @POST
    @Path("/incoming-request")
    @Produces(MediaType.APPLICATION_JSON)
    Response submitRequest(IntakeRequest intakeDto);

}
