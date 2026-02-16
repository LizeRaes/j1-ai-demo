package com.medicalappointment.ticketing.mapper;

import com.medicalappointment.ticketing.domain.model.TicketComment;
import com.medicalappointment.ticketing.dto.CommentDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CommentMapper {
    public CommentDto toDto(TicketComment comment) {
        CommentDto dto = new CommentDto();
        dto.id = comment.id;
        dto.ticketId = comment.ticketId;
        dto.authorId = comment.authorId;
        dto.body = comment.body;
        dto.createdAt = comment.createdAt;
        return dto;
    }
}
