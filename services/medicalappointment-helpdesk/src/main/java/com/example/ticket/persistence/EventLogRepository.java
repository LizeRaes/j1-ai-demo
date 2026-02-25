package com.example.ticket.persistence;

import com.example.ticket.domain.model.EventLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Date;
import java.util.List;

@ApplicationScoped
public class EventLogRepository implements PanacheRepository<EventLog> {
    public List<EventLog> findRecentSince(Date since, int limit) {
        return find("createdAt > ?1 ORDER BY createdAt ASC", since)
                .page(0, limit)
                .list();
    }

    public List<EventLog> findRecent(int limit) {
        // Get most recent events in chronological order (oldest first)
        // Frontend will reverse to display newest at top
        return find("ORDER BY createdAt ASC")
                .page(0, limit)
                .list();
    }
}
