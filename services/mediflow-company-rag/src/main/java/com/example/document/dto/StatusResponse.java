package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StatusResponse {
    @JsonProperty("status")
    private String status;

    public StatusResponse() {
    }

    public StatusResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
