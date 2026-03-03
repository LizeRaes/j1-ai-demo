package com.example.appointment.persistence;

import com.example.appointment.domain.model.TicketPullRequest;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TicketPullRequestRepository implements PanacheRepository<TicketPullRequest> {

    public List<TicketPullRequest> findByTicketId(Long ticketId) {
        return find("ticket.id = ?1 order by id desc", ticketId).list();
    }

    public TicketPullRequest findAiPullRequestByTicketId(Long ticketId) {
        return find("ticket.id = ?1 and aiGenerated = true", ticketId).firstResult();
    }
}
