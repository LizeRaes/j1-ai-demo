package com.example.appointment.resource;

import com.example.appointment.services.AccountService;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
            String message = URLEncoder.encode("Password reset email sent", StandardCharsets.UTF_8);
            return Response.seeOther(URI.create("/account?message=" + message))
                    .build();
        } else {
            String error = URLEncoder.encode("Password reset failed", StandardCharsets.UTF_8);
            return Response.seeOther(URI.create("/account?error=" + error))
                    .build();
        }
    }
}
