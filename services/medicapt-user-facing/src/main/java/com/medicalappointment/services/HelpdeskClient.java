package com.medicalappointment.services;

import com.medicalappointment.model.HelpdeskRequest;
import com.medicalappointment.model.IncomingRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class HelpdeskClient {
    
    @Inject
    @ConfigProperty(name = "helpdesk.endpoint", defaultValue = "http://localhost:8080/api/intake/incoming-request")
    String helpdeskEndpoint;

    public String submitRequest(IncomingRequest request) {
        try {
            // Map from IncomingRequest to HelpdeskRequest format
            HelpdeskRequest helpdeskRequest = new HelpdeskRequest(
                    request.getUserId(),
                    request.getRawText() // map rawText to message
            );
            
            System.out.println("=== HELPDESK SUBMISSION ===");
            System.out.println("Endpoint: " + helpdeskEndpoint);
            System.out.println("Request: userId=" + helpdeskRequest.getUserId() + ", message=" + helpdeskRequest.getMessage());
            
            // Manually serialize to JSON to ensure correct format
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonPayload = objectMapper.writeValueAsString(helpdeskRequest);
            System.out.println("JSON payload: " + jsonPayload);
            
            Client client = ClientBuilder.newClient();
            Response response = null;
            String responseBody = null;
            
            try {
                response = client.target(helpdeskEndpoint)
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.entity(jsonPayload, MediaType.APPLICATION_JSON));
                
                int status = response.getStatus();
                
                // Try to read response body - handle both success and error cases
                try {
                    if (response.hasEntity()) {
                        responseBody = response.readEntity(String.class);
                    }
                } catch (Exception readEx) {
                    // If reading fails, try bufferEntity first
                    try {
                        response.bufferEntity();
                        responseBody = response.readEntity(String.class);
                    } catch (Exception e2) {
                        responseBody = "(could not read response body: " + e2.getMessage() + ")";
                    }
                }
                
                System.out.println("Response status: " + status);
                System.out.println("Response body: " + (responseBody != null ? responseBody : "(empty or null)"));
                
                if (status == 200 || status == 201) {
                    System.out.println("✓ Helpdesk request submitted successfully");
                    System.out.println("===========================");
                    if (response != null) response.close();
                    client.close();
                    return null; // null means success
                } else {
                    String errorMsg = "HTTP " + status;
                    if (responseBody != null && !responseBody.trim().isEmpty() && !responseBody.startsWith("(could not read")) {
                        errorMsg += " - " + responseBody;
                    } else {
                        errorMsg += " - Bad Request (check endpoint and request format)";
                    }
                    System.err.println("✗ " + errorMsg);
                    System.err.println("===========================");
                    if (response != null) response.close();
                    client.close();
                    return errorMsg;
                }
            } finally {
                // Ensure cleanup
                if (response != null) {
                    try {
                        response.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
                try {
                    client.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        } catch (jakarta.ws.rs.ProcessingException e) {
            String errorMsg = "Connection error: " + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += " (Cause: " + e.getCause().getMessage() + ")";
            }
            System.err.println("✗ " + errorMsg);
            System.err.println("Full exception:");
            e.printStackTrace();
            System.err.println("===========================");
            return errorMsg;
        } catch (Exception e) {
            String errorMsg = "Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            System.err.println("✗ " + errorMsg);
            System.err.println("Full exception:");
            e.printStackTrace();
            System.err.println("===========================");
            return errorMsg;
        }
    }
}
