package com.example.ticket.persistence;

import com.example.ticket.domain.constants.RequestStatus;
import com.example.ticket.domain.model.IncomingRequest;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class IncomingRequestRepository implements PanacheRepository<IncomingRequest> {
    public List<IncomingRequest> findByStatus(RequestStatus status) {
        return find("status", status).list();
    }
}
