package com.example.ticket.dto;

public sealed interface MappedStatusResponse permits StatusResponse {

    default StatusResponse asStatusResponse() {
        return switch (this) {
            case StatusResponse response -> response;
        };
    }
}
