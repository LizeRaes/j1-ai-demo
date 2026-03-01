package com.example.aicodingassistant.api;

import com.example.aicodingassistant.dto.CallbackResultRequest;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ApplicationScoped
public class CallbackClient {
    @ConfigProperty(name = "app.callback.auth-token")
    String callbackAuthToken;

    @ConfigProperty(name = "quarkus.rest-client.helpdesk-callback.url")
    String callbackUrl;

    private HelpdeskCallbackApi callbackApi;

    @PostConstruct
    void init() {
        callbackApi = RestClientBuilder.newBuilder()
                .baseUri(URI.create(callbackUrl))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(HelpdeskCallbackApi.class);
    }

    public void postResult(CallbackResultRequest payload, Consumer<String> logger) {
        logger.accept("Callback target: " + callbackUrl);
        String authHeader = callbackAuthToken.startsWith("Bearer ")
                ? callbackAuthToken
                : "Bearer " + callbackAuthToken;
        try (Response response = callbackApi.postResult(payload, authHeader)) {
            int status = response.getStatus();
            String body = response.hasEntity() ? response.readEntity(String.class) : "";
            logger.accept("Callback response: HTTP " + status + (body != null && !body.isBlank() ? " " + body : ""));
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Helpdesk callback returned non-2xx status: " + status);
            }
        }
        logger.accept("Callback delivered for ticket " + payload.ticketId());
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    interface HelpdeskCallbackApi {
        @POST
        Response postResult(
                CallbackResultRequest payload,
                @HeaderParam("Authorization") String authorization
        );
    }
}
