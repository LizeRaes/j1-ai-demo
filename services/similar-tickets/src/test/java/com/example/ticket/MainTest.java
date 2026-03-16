package com.example.ticket;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.testing.junit5.ServerTest;

import com.example.ticket.dto.SearchRequest;
import com.example.ticket.dto.TicketsResponse;
import com.example.ticket.dto.UpsertRequest;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
//@EnabledIfSystemProperty(named = "openai.api-key", matches = ".+")
@EnabledIfSystemProperty(named = "data.sources.sql[0].provider.ucp.url", matches = "jdbc:oracle:thin:.*")
@ServerTest
class MainTest  {
    private final Http1Client client;

    protected MainTest(Http1Client client) {
        this.client = client;
    }

    @Test
    void testConfigRoute() {
        try (Http1ClientResponse response = client
                .get("/api/similarity/tickets/config")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getJsonNumber("defaultZoom").toString(), is("100"));
        }
    }

    @Test
    void testLogsRoute() {
        try (Http1ClientResponse response = client
                .get("/api/similarity/tickets/logs")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertNotNull(json);
            assertNotNull(json.getJsonArray("logs"));
        }
    }

    @Test
    void testAll() {
        try (Http1ClientResponse response = client
                .get("/api/similarity/tickets/all")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            var json = response.as(TicketsResponse.class);
            assertNotNull(json);
            assertNotNull(json.tickets());
        }
    }


    @Test
    void testDelete() {
        try (Http1ClientResponse response = client
                .delete("/api/similarity/tickets/delete/912")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    void testSearch() {
        try (Http1ClientResponse response = client
                .post("/api/similarity/tickets/search")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit(new SearchRequest("string", "reschedule button disabled", 5, 0.7, 912L))) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertNotNull(json);
            assertNotNull(json.getJsonArray("relatedTicketIds"));
        }
    }

    @Test
    void testUpsert() {
        try (Http1ClientResponse response = client
                .post("/api/similarity/tickets/upsert")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit(new UpsertRequest(912L, "BUG_APP", "The reschedule button is disabled on my appointment"))) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertNotNull(json);
        }
    }

    @Test
    void testMetricsObserver() {
        try (Http1ClientResponse response = client.get("/observe/metrics").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }
}