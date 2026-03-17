package com.example.ticket.dto;

import java.util.List;

public record SearchResponse(List<Long> relatedTicketIds) {
}
