package com.example.ticket.model;

public sealed interface MappedTicketSearchQuery permits TicketSearchQuery {

    default TicketSearchQuery asTicketSearchQuery() {
        return switch (this) {
            case TicketSearchQuery query -> query;
        };
    }
}
