package org.example.similarity;

import dev.langchain4j.internal.Json;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RoutingTest
class MainTest  {
    private final Http1Client client;

    protected MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        Main.routing(routing);
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
            JsonObject json = response.as(JsonObject.class);
            assertNotNull(json);
            assertNotNull(json.getJsonArray("tickets"));
            assertTrue(json.getJsonArray("tickets").size() > 0);
        }
    }


    @Test
    void testDelete() {
        try (Http1ClientResponse response = client
                .post("/api/similarity/tickets/delete")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit(Json.toJson("""
                        {
                          "ticketId": 912
                        }
                        """))) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    void testSearch() {
        try (Http1ClientResponse response = client
                .post("/api/similarity/tickets/search")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit("""
                        {
                          "text": "reschedule button disabled",
                           ticketType": "string",
                          "ticketId": 912,
                          "maxResults": 5,
                          "minScore": 0.7
                        }
                        """)) {
            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertNotNull(json);
            assertNotNull(json.getJsonArray("relatedTicketIds"));
            assertTrue(json.getJsonArray("relatedTicketIds").size() > 0);
        }
    }

    @Test
    void testUpsert() {
        try (Http1ClientResponse response = client
                .post("/api/similarity/tickets/search")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit("""
                        {
                          "ticketId": 912,
                          "ticketType": "BUG_APP",
                          "text": "The reschedule button is disabled on my appointment."
                        }
                        """)) {
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