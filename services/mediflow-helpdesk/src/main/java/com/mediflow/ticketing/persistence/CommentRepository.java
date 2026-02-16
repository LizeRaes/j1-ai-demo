package com.mediflow.ticketing.persistence;

import com.mediflow.ticketing.domain.model.TicketComment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class CommentRepository implements PanacheRepository<TicketComment> {
    public List<TicketComment> findByTicketId(Long ticketId) {
        return find("ticketId", Sort.ascending("createdAt"), ticketId).list();
    }
}
