package com.example.appointment.logging;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@ApplicationScoped
public class JobLogStream {
    private static final Logger LOG = Logger.getLogger(JobLogStream.class);

    private final List<Consumer<JobLogService.LogEntry>> listeners = new CopyOnWriteArrayList<>();

    public void append(JobLogService.LogEntry entry) {
        listeners.forEach(listener -> {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                LOG.debug("Listener error (likely disconnected client)", e);
            }
        });
    }

    public void subscribe(Consumer<JobLogService.LogEntry> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<JobLogService.LogEntry> listener) {
        listeners.remove(listener);
    }

    public Multi<JobLogService.LogEntry> stream() {
        return Multi.createFrom().emitter(emitter -> {
            Consumer<JobLogService.LogEntry> listener = emitter::emit;
            subscribe(listener);
            emitter.onTermination(() -> unsubscribe(listener));
        });
    }
}
