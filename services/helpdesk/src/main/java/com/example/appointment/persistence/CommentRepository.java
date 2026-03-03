package com.example.appointment.persistence;

import com.example.appointment.domain.model.Comment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CommentRepository implements PanacheRepository<Comment> {
    public List<Comment> findByTicketId(Long ticketId) {
        return find("ticket.id", Sort.ascending("createdAt"), ticketId).list();
    }
}
