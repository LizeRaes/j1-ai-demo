package com.example.appointment.external;

import com.example.appointment.dto.SimilaritySearchRequest;
import com.example.appointment.dto.SimilaritySearchResponse;
import com.example.appointment.dto.TicketSyncUpsertRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "similarity")
@Path("/api/similarity")
public interface SimilarityClient {

    @POST
    @Path("/tickets/search")
    @Retry(maxRetries = 1, delay = 100)
    @Timeout(5000)
    @Fallback(fallbackMethod = "fallbackSearch")
    SimilaritySearchResponse search(SimilaritySearchRequest request);

    @POST
    @Path("/tickets/upsert")
    Response upsert(TicketSyncUpsertRequest request);

    @DELETE
    @Path("/tickets/delete/{ticketId}")
    Response delete(@PathParam("ticketId") Long ticketId);

    default SimilaritySearchResponse fallbackSearch(SimilaritySearchRequest request) {
        return new SimilaritySearchResponse(List.of());
    }
}

