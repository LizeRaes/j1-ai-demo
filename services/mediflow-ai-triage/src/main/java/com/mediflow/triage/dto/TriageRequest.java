package com.mediflow.triage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TriageRequest {
    @JsonProperty("incomingRequestId")
    private Long incomingRequestId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("allowedTicketTypes")
    private List<TicketTypeInfo> allowedTicketTypes;

    @JsonProperty("ticketId")
    private Integer ticketId;

    public TriageRequest() {
    }

    public Long getIncomingRequestId() {
        return incomingRequestId;
    }

    public void setIncomingRequestId(Long incomingRequestId) {
        this.incomingRequestId = incomingRequestId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<TicketTypeInfo> getAllowedTicketTypes() {
        return allowedTicketTypes;
    }

    public void setAllowedTicketTypes(List<TicketTypeInfo> allowedTicketTypes) {
        this.allowedTicketTypes = allowedTicketTypes;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }

    public static class TicketTypeInfo {
        @JsonProperty("type")
        private String type;

        @JsonProperty("description")
        private String description;

        public TicketTypeInfo() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
