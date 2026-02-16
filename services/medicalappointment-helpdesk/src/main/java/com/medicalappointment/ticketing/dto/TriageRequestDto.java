package com.medicalappointment.ticketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TriageRequestDto {
    @JsonProperty("incomingRequestId")
    public Long incomingRequestId;
    
    @JsonProperty("message")
    public String message;
    
    @JsonProperty("ticketId")
    public Long ticketId;
    
    @JsonProperty("allowedTicketTypes")
    public List<AllowedTicketType> allowedTicketTypes;
    
    public static class AllowedTicketType {
        @JsonProperty("type")
        public String type;
        
        @JsonProperty("description")
        public String description;
    }
}
