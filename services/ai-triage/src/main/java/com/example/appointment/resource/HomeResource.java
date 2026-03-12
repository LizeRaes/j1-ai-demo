package com.example.appointment.resource;

import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/")
public class HomeResource {

    @Inject
    Template index;

    @ConfigProperty(name = "ai-triage.ui.default-zoom-percent")
    Integer defaultZoomPercent;

    @ConfigProperty(name = "ai-triage.ui.polling.enabled")
    Boolean pollingEnabled;

    @ConfigProperty(name = "ai-triage.ui.polling.interval-ms")
    Integer pollingIntervalMs;

    @ConfigProperty(name = "ai-triage.ui.show-event-log", defaultValue = "false")
    Boolean showEventLog;

    @ConfigProperty(name = "ai-triage.helpdesk.base-url")
    String helpdeskBaseUrl;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index() {
        return index
                .data("defaultZoomPercent", defaultZoomPercent)
                .data("pollingEnabled", pollingEnabled)
                .data("pollingIntervalMs", pollingIntervalMs)
                .data("showEventLog", showEventLog)
                .data("documentsApiBase", "/api/documents")
                .data("helpdeskAppBase", helpdeskBaseUrl)
                .render();
    }

}