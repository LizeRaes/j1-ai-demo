package com.example.ticket.external;

import com.example.ticket.dto.SimilarityResponseDto;
import com.example.ticket.dto.SimilarityUpsertRequestDto;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.*;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Path("/api/similarity/")
@RegisterRestClient
public interface SimilarityClient {

    @POST
    @Path("/tickets/upsert")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 1, delay = 1000)
    @Timeout(300)
    @Fallback(AsyncTicketFallback.class)
    CompletionStage<SimilarityResponseDto> upsertTicketAsync(SimilarityUpsertRequestDto request);


    class AsyncTicketFallback implements FallbackHandler<CompletionStage<SimilarityResponseDto>> {

        private static final SimilarityResponseDto EMPTY_SIMILARITY_RESPONSE = new SimilarityResponseDto("Similarity service did not respond in time");

        @Override
        public CompletionStage<SimilarityResponseDto> handle(ExecutionContext context) {
            return CompletableFuture.supplyAsync(() -> EMPTY_SIMILARITY_RESPONSE);
        }

    }
}
