package com.mediflow.resources;

import com.mediflow.model.IncomingRequest;
import com.mediflow.services.AccountService;
import com.mediflow.services.HelpdeskClient;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/help")
public class HelpResource {
    @Inject
    Template help;

    @Inject
    AccountService accountService;

    @Inject
    HelpdeskClient helpdeskClient;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String help(@QueryParam("submitted") String submitted, @QueryParam("error") String error) {
        return help.data("submitted", submitted != null)
                .data("error", error)
                .render();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submitHelp(IncomingRequest request) {
        // If userId not provided, use default
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            request.setUserId(accountService.getUserId());
        }
        
        // Submit to helpdesk
        String errorMsg = helpdeskClient.submitRequest(request);
        
        if (errorMsg == null) {
            return Response.ok().entity("{\"status\": \"submitted\"}").build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"status\": \"error\", \"message\": \"" + errorMsg.replace("\"", "\\\"") + "\"}")
                    .build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response submitHelpForm(@FormParam("message") String message) {
        String userId = accountService.getUserId();
        IncomingRequest request = new IncomingRequest(userId, "web", message);
        
        String errorMsg = helpdeskClient.submitRequest(request);
        
        if (errorMsg == null) {
            // Success - no error message
            return Response.seeOther(java.net.URI.create("/help?submitted=true"))
                    .build();
        } else {
            // Failure - pass error message as URL parameter (URL encoded)
            String encodedError = java.net.URLEncoder.encode(errorMsg, java.nio.charset.StandardCharsets.UTF_8);
            return Response.seeOther(java.net.URI.create("/help?error=" + encodedError))
                    .build();
        }
    }
}
