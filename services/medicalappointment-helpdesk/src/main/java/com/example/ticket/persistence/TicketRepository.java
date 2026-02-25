package com.example.ticket.persistence;

import com.example.ticket.domain.constants.TicketStatus;
import com.example.ticket.domain.model.Ticket;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TicketRepository implements PanacheRepository<Ticket> {

    public List<Ticket> findByStatus(TicketStatus status) {
        return find("status", status).list();
    }

    public List<Ticket> findByAssignedTeam(String team) {
        return find("assignedTeam", team).list();
    }

    public List<Ticket> findByAssignedTo(String userId) {
        return find("assignedTo", userId).list();
    }
}
