package com.example.ticket.model;

public sealed interface MappedTicket permits TicketData {

    default TicketData asTicketData() {
        return switch (this) {
            case TicketData ticket -> ticket;
        };
    }
}
