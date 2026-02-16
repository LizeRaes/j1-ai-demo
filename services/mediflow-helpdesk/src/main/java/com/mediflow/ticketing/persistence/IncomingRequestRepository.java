package com.mediflow.ticketing.persistence;

import com.mediflow.ticketing.domain.enums.RequestStatus;
import com.mediflow.ticketing.domain.model.IncomingRequest;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class IncomingRequestRepository implements PanacheRepository<IncomingRequest> {
    public List<IncomingRequest> findByStatus(RequestStatus status) {
        return find("status", status).list();
    }
}
