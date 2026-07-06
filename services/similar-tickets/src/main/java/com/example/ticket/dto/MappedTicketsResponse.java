package com.example.ticket.dto;

public sealed interface MappedTicketsResponse permits TicketsResponse {

    default TicketsResponse asTicketsResponse() {
        return switch (this) {
            case TicketsResponse response -> response;
        };
    }
}
