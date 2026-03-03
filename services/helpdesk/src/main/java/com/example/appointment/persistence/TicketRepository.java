package com.example.appointment.persistence;

import com.example.appointment.domain.constants.TicketStatus;
import com.example.appointment.domain.model.Ticket;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TicketRepository implements PanacheRepository<Ticket> {

    public void resetAutoIncrement() {
        getEntityManager().createNativeQuery("ALTER TABLE tickets AUTO_INCREMENT = 1").executeUpdate();
    }

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
