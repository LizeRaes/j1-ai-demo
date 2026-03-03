package com.example.appointment.resource;

import com.example.appointment.dto.EventDto;
import com.example.appointment.service.adapter.EventService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Path("/api/events")
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {

    @Inject
    EventService eventService;

    @ConfigProperty(name = "event.limit.max")
    int maxEventLimit;

    @GET
    @Path("/recent")
    public List<EventDto> getRecentEvents(
            @QueryParam("since") LocalDateTime dateTime,
            @QueryParam("limit") Integer limit) {
        int eventLimit = limit != null ? Math.min(limit, maxEventLimit) : maxEventLimit;
        return eventService.getRecentEvents(dateTime, eventLimit);
    }
}
