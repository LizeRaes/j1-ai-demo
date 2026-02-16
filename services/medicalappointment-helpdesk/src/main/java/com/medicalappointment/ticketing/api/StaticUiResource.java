package com.medicalappointment.ticketing.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;

@Path("/")
public class StaticUiResource {
    @GET
    public Response index() {
        InputStream indexStream = getClass().getClassLoader()
            .getResourceAsStream("META-INF/resources/index.html");
        if (indexStream == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(indexStream).build();
    }
}
