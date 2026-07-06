package com.example.ticket.rest;

import java.util.Collection;
import java.util.List;

import io.helidon.http.BadRequestException;

import com.example.ticket.dto.MappedSearchResponse;
import com.example.ticket.dto.MappedStatusResponse;
import com.example.ticket.dto.MappedTicketsResponse;
import com.example.ticket.dto.MappedUpsertRequest;
import com.example.ticket.dto.SearchRequest;
import com.example.ticket.dto.SearchResponse;
import com.example.ticket.dto.StatusResponse;
import com.example.ticket.dto.TicketsResponse;
import com.example.ticket.dto.UpsertRequest;
import com.example.ticket.model.MappedTicket;
import com.example.ticket.model.MappedTicketSearchQuery;
import com.example.ticket.model.SimilarTicket;
import com.example.ticket.model.TicketData;
import com.example.ticket.model.TicketSearchQuery;

final class SimilarityMapper {

    private SimilarityMapper() {
    }

    static MappedTicketSearchQuery toSearchQuery(SearchRequest request, SimilaritySearchDefaults defaults) {
        return switch (request) {
            case null -> throw new BadRequestException("text and id are required");
            case SearchRequest r when r.text() == null || r.ticketId() == null ->
                    throw new BadRequestException("text and id are required");
            case SearchRequest r -> toValidatedSearchQuery(r, defaults);
        };
    }

    static MappedUpsertRequest validateUpsertRequest(MappedUpsertRequest request) {
        return switch (request) {
            case null -> throw new BadRequestException("id, type, and text are required");
            case UpsertRequest r when r.ticketId() == null || r.text() == null || r.ticketType() == null ->
                    throw new BadRequestException("id, type, and text are required");
            case UpsertRequest r -> r;
        };
    }

    static MappedTicket toTicketData(MappedUpsertRequest request, float[] embedding) {
        return switch (request) {
            case null -> throw new BadRequestException("id, type, and text are required");
            case UpsertRequest r when r.ticketId() == null || r.text() == null || r.ticketType() == null ->
                    throw new BadRequestException("id, type, and text are required");
            case UpsertRequest r -> new TicketData(
                    r.ticketId(),
                    r.ticketType(),
                    r.text(),
                    embedding,
                    System.currentTimeMillis());
        };
    }

    static MappedStatusResponse toStatusResponse(String status) {
        return new StatusResponse(status);
    }

    static MappedSearchResponse toSearchResponse(List<SimilarTicket> results) {
        return switch (results) {
            case null -> new SearchResponse(List.of());
            case List<SimilarTicket> matches -> new SearchResponse(matches.stream()
                    .map(SimilarTicket::ticketId)
                    .toList());
        };
    }

    static MappedTicketsResponse toTicketsResponse(Collection<TicketData> tickets) {
        return switch (tickets) {
            case null -> new TicketsResponse(List.of());
            case Collection<TicketData> values -> new TicketsResponse(values.stream()
                    .sorted((a, b) -> Long.compare(b.ticketId(), a.ticketId()))
                    .map(ticket -> new TicketsResponse.TicketInfo(
                            ticket.ticketId(),
                            ticket.ticketType(),
                            ticket.text(),
                            ticket.vector()))
                    .toList());
        };
    }

    private static TicketSearchQuery toValidatedSearchQuery(SearchRequest request, SimilaritySearchDefaults defaults) {
        int resultLimit = request.maxResults() != null ? request.maxResults() : defaults.maxResults();
        double scoreThreshold = request.minScore() != null ? request.minScore() : defaults.minScore();
        try {
            return new TicketSearchQuery(request.text(), resultLimit, scoreThreshold, request.ticketId());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

}
