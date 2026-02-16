package com.medicalappointment.ticketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TriageResponseDto {
    @JsonProperty("status")
    public String status; // "OK" or "FAILED"
    
    @JsonProperty("ticketType")
    public String ticketType;
    
    @JsonProperty("urgencyScore")
    public Integer urgencyScore;
    
    @JsonProperty("aiConfidencePercent")
    public Integer aiConfidencePercent;
    
    @JsonProperty("relatedTicketIds")
    public List<Long> relatedTicketIds;
    
    @JsonProperty("policyCitations")
    public List<PolicyCitation> policyCitations;
    
    @JsonProperty("failReason")
    public String failReason; // Only present when status=FAILED
    
    public static class PolicyCitation {
        @JsonProperty("documentName")
        public String documentName;
        
        @JsonProperty("documentLink")
        public String documentLink;
        
        @JsonProperty("citation")
        public String citation;
        
        @JsonProperty("score")
        public Double score;
        
        @JsonProperty("rbacTeams")
        public List<String> rbacTeams;
    }
}
