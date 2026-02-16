package com.mediflow.triage.resource;

import com.mediflow.triage.dto.Event;
import com.mediflow.triage.dto.TicketView;
import com.mediflow.triage.service.EventLogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/")
public class UIResource {

    @Inject
    EventLogService eventLogService;

    @ConfigProperty(name = "ai-triage.ui.default-zoom-percent", defaultValue = "100")
    Integer defaultZoomPercent;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getUI() {
        try {
            InputStream htmlStream = getClass().getClassLoader().getResourceAsStream("META-INF/resources/ui/index.html");
            if (htmlStream == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("UI not found").build();
            }
            String html = new String(htmlStream.readAllBytes(), StandardCharsets.UTF_8);
            return Response.ok(html).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error loading UI: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/api/ui/events")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Event> getEvents() {
        return eventLogService.getEvents();
    }

    @GET
    @Path("/api/ui/tickets")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TicketView> getTickets() {
        return eventLogService.getTickets();
    }

    @GET
    @Path("/api/ui/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("defaultZoomPercent", defaultZoomPercent);
        return config;
    }
}
