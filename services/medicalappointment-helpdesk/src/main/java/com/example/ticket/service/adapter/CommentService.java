package com.example.ticket.service.adapter;

import com.example.ticket.domain.model.Comment;
import com.example.ticket.persistence.CommentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class CommentService {

    @Inject
    CommentRepository commentRepository;

    @Transactional
    public List<Comment> findByTicketId(Long ticketId) {
        return commentRepository.findByTicketId(ticketId);
    }

    @Transactional
    public void persist(Comment comment) {
        commentRepository.persist(comment);
    }

    @Transactional
    public void deleteAll() {
        commentRepository.deleteAll();
    }
}