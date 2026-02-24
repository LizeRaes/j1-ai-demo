package com.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentDto (Long id, Long ticketId, String authorId, String body) {

}
