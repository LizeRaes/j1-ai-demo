package com.medicalappointment.ticketing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

/**
 * Customizes Quarkus's default ObjectMapper to handle Java 8 time types.
 * This affects both REST endpoints and our TriageClient.
 */
@Singleton
public class ObjectMapperProducer implements ObjectMapperCustomizer {
    
    @Override
    public void customize(ObjectMapper mapper) {
        // Register Java 8 time module to handle OffsetDateTime, LocalDateTime, etc.
        mapper.registerModule(new JavaTimeModule());
        // Disable writing dates as timestamps - use ISO-8601 strings instead
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
