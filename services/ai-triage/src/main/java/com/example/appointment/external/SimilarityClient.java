package com.example.appointment.external;

import com.example.appointment.dto.SimilaritySearchRequest;
import com.example.appointment.dto.SimilaritySearchResponse;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "similarity")
@Path("/api/similarity")
public interface SimilarityClient {

    @POST
    @Path("/tickets/search")
    @Retry(maxRetries = 1, delay = 100)
    @Timeout(5000)
    SimilaritySearchResponse search(SimilaritySearchRequest request);
}

