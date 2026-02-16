package com.mediflow.ticketing.mapper;

import com.mediflow.ticketing.domain.model.TicketComment;
import com.mediflow.ticketing.dto.CommentDto;
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
