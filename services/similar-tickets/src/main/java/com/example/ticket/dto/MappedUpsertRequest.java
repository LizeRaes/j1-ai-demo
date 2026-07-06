package com.example.ticket.dto;

public sealed interface MappedUpsertRequest permits UpsertRequest {

    default UpsertRequest asUpsertRequest() {
        return switch (this) {
            case UpsertRequest request -> request;
        };
    }
}
