package com.example.ticket.resource;

import com.example.ticket.dto.EventDto;
import com.example.ticket.service.EventService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {
    private static final Logger LOGGER = Logger.getLogger(EventResource.class.getName());

    @Inject
    EventService eventService;

    @ConfigProperty(name = "event.limit.max")
    int maxEventLimit;

    @GET
    @Path("/recent")
    public List<EventDto> getRecentEvents(
            @QueryParam("since") String sinceStr,
            @QueryParam("limit") Integer limit) {
        Date since = Date.valueOf(LocalDateTime.now().toLocalDate());
        if (sinceStr != null && !sinceStr.isEmpty()) {
            try {
                since = Date.valueOf(sinceStr);
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Invalid date given %s".formatted(sinceStr));
            }
        }
        int eventLimit = limit != null ? Math.min(limit, maxEventLimit) : maxEventLimit;
        return eventService.getRecentEvents(since, eventLimit);
    }
}
