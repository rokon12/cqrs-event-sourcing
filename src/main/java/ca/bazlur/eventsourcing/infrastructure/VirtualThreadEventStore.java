package ca.bazlur.eventsourcing.infrastructure;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.EventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class VirtualThreadEventStore implements EventStore {
    
    private static final Logger log = LoggerFactory.getLogger(VirtualThreadEventStore.class);
    
    private static final String INSERT_EVENT_SQL = """
        INSERT INTO events (event_id, stream_id, event_type, event_data, version, timestamp, correlation_id, causation_id)
        VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
        """;
    
    private static final String SELECT_EVENTS_SQL = """
        SELECT event_id, stream_id, event_type, event_data, version, timestamp, correlation_id, causation_id
        FROM events
        WHERE stream_id = ?
        ORDER BY version ASC
        """;
    
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore concurrencyLimit = new Semaphore(10_000);
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final String databaseUrl;
    
    @Inject
    public VirtualThreadEventStore(ObjectMapper objectMapper, Tracer tracer) {
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.databaseUrl = System.getProperty("datasource.url", "jdbc:postgresql://localhost:5432/eventstore");
    }
    
    @Override
    @WithSpan("eventstore.append")
    public CompletableFuture<Void> appendEvents(String streamId, List<DomainEvent> events, long expectedVersion) {
        return CompletableFuture.runAsync(() -> {
            try {
                concurrencyLimit.acquire();
                
                try (Connection conn = DriverManager.getConnection(databaseUrl);
                     PreparedStatement stmt = conn.prepareStatement(INSERT_EVENT_SQL)) {
                    
                    conn.setAutoCommit(false);
                    
                    for (DomainEvent event : events) {
                        stmt.setString(1, event.getEventId());
                        stmt.setString(2, streamId);
                        stmt.setString(3, event.getEventType());
                        stmt.setString(4, objectMapper.writeValueAsString(event));
                        stmt.setLong(5, event.getVersion());
                        stmt.setTimestamp(6, java.sql.Timestamp.from(event.getTimestamp()));
                        stmt.setString(7, event.getCorrelationId());
                        stmt.setString(8, event.getCausationId());
                        stmt.addBatch();
                    }
                    
                    stmt.executeBatch();
                    conn.commit();
                    
                    log.debug("Appended {} events to stream {}", events.size(), streamId);
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to append events to stream: " + streamId, e);
                } finally {
                    concurrencyLimit.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for concurrency permit", e);
            }
        }, virtualThreadExecutor);
    }
    
    @Override
    @WithSpan("eventstore.get-events")
    public CompletableFuture<List<DomainEvent>> getEvents(String streamId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                concurrencyLimit.acquire();
                
                try (Connection conn = DriverManager.getConnection(databaseUrl);
                     PreparedStatement stmt = conn.prepareStatement(SELECT_EVENTS_SQL)) {
                    
                    stmt.setString(1, streamId);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        List<DomainEvent> events = new ArrayList<>();
                        
                        while (rs.next()) {
                            String eventData = rs.getString("event_data");
                            DomainEvent event = objectMapper.readValue(eventData, DomainEvent.class);
                            events.add(event);
                        }
                        
                        log.debug("Loaded {} events from stream {}", events.size(), streamId);
                        return events;
                    }
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load events from stream: " + streamId, e);
                } finally {
                    concurrencyLimit.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for concurrency permit", e);
            }
        }, virtualThreadExecutor);
    }
    
    @Override
    public CompletableFuture<List<DomainEvent>> getEvents(String streamId, long fromVersion) {
        return getEvents(streamId); // Simplified for demo
    }
    
    @Override
    public CompletableFuture<List<DomainEvent>> getAllEvents() {
        return CompletableFuture.completedFuture(new ArrayList<>()); // Simplified for demo
    }
    
    @Override
    public CompletableFuture<List<DomainEvent>> getAllEvents(long fromVersion) {
        return getAllEvents(); // Simplified for demo
    }
    
    @PreDestroy
    public void shutdown() {
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}