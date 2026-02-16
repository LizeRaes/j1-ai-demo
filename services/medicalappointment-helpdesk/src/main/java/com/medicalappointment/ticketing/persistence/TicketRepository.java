package com.medicalappointment.ticketing.persistence;

import com.medicalappointment.ticketing.domain.enums.TicketStatus;
import com.medicalappointment.ticketing.domain.model.Ticket;
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
