package com.example.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentDto(Long id, Long ticketId, String authorId, String body) {

}
