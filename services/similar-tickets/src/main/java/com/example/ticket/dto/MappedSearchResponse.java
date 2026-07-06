package com.example.ticket.dto;

public sealed interface MappedSearchResponse permits SearchResponse {

    default SearchResponse asSearchResponse() {
        return switch (this) {
            case SearchResponse response -> response;
        };
    }
}
