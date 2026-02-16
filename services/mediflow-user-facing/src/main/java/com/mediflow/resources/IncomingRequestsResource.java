package com.mediflow.resources;

import com.mediflow.model.IncomingRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/incoming-requests")
public class IncomingRequestsResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response receiveRequest(IncomingRequest request) {
        // Log the incoming request
        System.out.println("=== INCOMING HELPDESK REQUEST ===");
        System.out.println("User ID: " + request.getUserId());
        System.out.println("Channel: " + request.getChannel());
        System.out.println("Raw Text: " + request.getRawText());
        System.out.println("=================================");
        
        // In a real system, this would process/store the ticket
        // For demo, just acknowledge receipt
        return Response.ok()
                .entity("{\"status\": \"received\", \"message\": \"Request logged\"}")
                .build();
    }
}
