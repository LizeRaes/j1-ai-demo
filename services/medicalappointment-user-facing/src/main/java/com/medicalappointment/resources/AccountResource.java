package com.medicalappointment.resources;

import com.medicalappointment.services.AccountService;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/account")
public class AccountResource {
    @Inject
    Template account;

    @Inject
    AccountService accountService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String account(@QueryParam("message") String message, @QueryParam("error") String error) {
        return account.data("userId", accountService.getUserId())
                .data("email", accountService.getEmail())
                .data("message", message)
                .data("error", error)
                .render();
    }

    @POST
    @Path("/reset-password")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response resetPassword() {
        String userId = accountService.getUserId();
        boolean success = accountService.resetPassword(userId);
        
        if (success) {
            return Response.seeOther(java.net.URI.create("/account?message=Password reset email sent"))
                    .build();
        } else {
            return Response.seeOther(java.net.URI.create("/account?error=Password reset failed"))
                    .build();
        }
    }
}
