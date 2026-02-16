package com.medicalappointment.ticketing.api;

import com.medicalappointment.ticketing.config.AppConfig;
import com.medicalappointment.ticketing.dto.EventDto;
import com.medicalappointment.ticketing.service.EventService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.util.List;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {
    @Inject
    EventService eventService;

    @GET
    @Path("/recent")
    public List<EventDto> getRecentEvents(
            @QueryParam("since") String sinceStr,
            @QueryParam("limit") Integer limit) {
        OffsetDateTime since = null;
        if (sinceStr != null && !sinceStr.isEmpty()) {
            try {
                since = OffsetDateTime.parse(sinceStr);
            } catch (Exception e) {
                // Invalid format, ignore
            }
        }
        int eventLimit = limit != null ? Math.min(limit, AppConfig.MAX_EVENT_LIMIT) : AppConfig.MAX_EVENT_LIMIT;
        return eventService.getRecentEvents(since, eventLimit);
    }
}
