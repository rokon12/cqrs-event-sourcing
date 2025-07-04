package ca.bazlur.eventsourcing.infrastructure;

import ca.bazlur.eventsourcing.core.*;
import ca.bazlur.eventsourcing.infrastructure.snapshot.SnapshotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JpaEventStore implements SnapshotEventStore {

    private static final Logger log = LoggerFactory.getLogger(JpaEventStore.class);

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final EventSchemaManager schemaManager;
    private final SnapshotService snapshotService;

    @Inject
    public JpaEventStore(
            EntityManager entityManager,
            ObjectMapper objectMapper,
            EventSchemaManager schemaManager,
            SnapshotService snapshotService
    ) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.schemaManager = schemaManager;
        this.snapshotService = snapshotService;
    }

    @Transactional
    public void appendEvents(String streamId, List<DomainEvent> events, long expectedVersion) {
        try {
            // Validate optimistic concurrency
            validateOptimisticConcurrency(streamId, expectedVersion);

            // Validate event schemas
            for (DomainEvent event : events) {
                schemaManager.validateEvent(event);
            }

            // Convert and store events
            var entities = events.stream()
                    .map(event -> new EventEntity(
                            event.getEventId(),
                            streamId,
                            event.getEventType(),
                            toJson(event),
                            event.getVersion(),
                            event.getTimestamp(),
                            event.getCorrelationId(),
                            event.getCausationId()))
                    .toList();

            entities.forEach(entityManager::persist);
            entityManager.flush();

            // After successfully appending events, check if we need to create a snapshot
            recordEventsAndManageSnapshot(streamId, events);

            log.debug("Appended {} events to stream {}", events.size(), streamId);
        } catch (EventSchemaException e) {
            log.error("Schema validation failed for events in stream {}: {}", streamId, e.getMessage());
            throw new EventStoreException("Schema validation failed", e);
        } catch (Exception e) {
            log.error("Failed to append events to stream {}: {}", streamId, e.getMessage());
            throw new EventStoreException("Failed to append events to stream: " + streamId, e);
        }
    }

    private void recordEventsAndManageSnapshot(String streamId, List<DomainEvent> events) {
        if (!events.isEmpty()) {
            try {
                // Get the aggregate type from the first event's class name
                var aggregateType = events.getFirst().getClass().getSimpleName()
                        .replaceAll("Event$", "");

                log.debug("Checking snapshot creation for stream {} of type {} after appending {} events",
                        streamId, aggregateType, events.size());

                // Load all events for this aggregate to reconstruct its current state
                var allEvents = getEvents(streamId);
                log.debug("Found {} total events for stream {}", allEvents.size(), streamId);

                var aggregateClass = Class.forName(
                        "ca.bazlur.eventsourcing.domain." +
                                aggregateType.toLowerCase() + "." + aggregateType);

                var aggregate = (AggregateRoot) aggregateClass.getConstructor(String.class)
                        .newInstance(streamId);
                aggregate.loadFromHistory(allEvents);

                log.info("Reconstructed aggregate {} of type {} at version {}",
                        streamId, aggregateType, aggregate.getVersion());

                // Try to create a snapshot if needed
                var snapshotCreated = snapshotService.createSnapshotIfNeeded(aggregate);
                if (snapshotCreated) {
                    log.info("Created new snapshot for aggregate {} of type {} at version {}",
                            streamId, aggregateType, aggregate.getVersion());
                } else {
                    log.debug("No snapshot needed for aggregate {} of type {} at version {}",
                            streamId, aggregateType, aggregate.getVersion());
                }
            } catch (Exception e) {
                // Log but don't fail the event append operation if snapshot creation fails
                log.warn("Failed to create snapshot for stream {} of type {} after appending {} events: {}",
                        streamId, events.getFirst().getClass().getSimpleName(), events.size(),
                        e.getMessage());
                log.debug("Snapshot creation failure details", e);
            }
        }
    }

    @Override
    public List<DomainEvent> getEvents(String streamId) {
        try {
            TypedQuery<EventEntity> query = entityManager.createQuery(
                    "SELECT e FROM EventEntity e WHERE e.streamId = :streamId ORDER BY e.version ASC",
                    EventEntity.class
            );
            query.setParameter("streamId", streamId);

            List<EventEntity> entities = query.getResultList();

            List<DomainEvent> events = entities.stream()
                    .map(this::deserializeEvent)
                    .toList();

            log.debug("Loaded {} events from stream {} (JPA)", events.size(), streamId);
            return events;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load events from stream: " + streamId, e);
        }
    }

    @Override
    public List<DomainEvent> getEvents(String streamId, long fromVersion) {
        try {
            TypedQuery<EventEntity> query = entityManager.createQuery(
                    "SELECT e FROM EventEntity e WHERE e.streamId = :streamId AND e.version >= :fromVersion ORDER BY e.version ASC",
                    EventEntity.class
            );
            query.setParameter("streamId", streamId);
            query.setParameter("fromVersion", fromVersion);

            List<EventEntity> entities = query.getResultList();

            return entities.stream()
                    .map(this::deserializeEvent)
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load events from stream: " + streamId, e);
        }
    }

    @Override
    public List<DomainEvent> getAllEvents() {
        return getAllEvents(0, Integer.MAX_VALUE);
    }

    @Override
    public List<DomainEvent> getAllEvents(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }
        if (limit > 1000) {
            log.warn("Requested limit {} is too large, limiting to 1000", limit);
            limit = 1000;
        }

        try {
            TypedQuery<EventEntity> query = entityManager.createQuery(
                    "SELECT e FROM EventEntity e ORDER BY e.timestamp ASC, e.version ASC",
                    EventEntity.class
            );
            query.setFirstResult(offset);
            query.setMaxResults(limit);

            List<EventEntity> entities = query.getResultList();

            var events = entities.stream()
                    .map(this::deserializeEvent)
                    .toList();

            log.debug("Loaded {} events with offset {} and limit {}", events.size(), offset, limit);
            return events;

        } catch (jakarta.persistence.PersistenceException e) {
            log.error("Database error while loading events with offset {} and limit {}", offset, limit, e);
            throw new EventStoreException("Database error while loading events", e);
        } catch (Exception e) {
            log.error("Failed to load events with offset {} and limit {}", offset, limit, e);
            throw new EventStoreException("Failed to load events", e);
        }
    }

    @Override
    public List<DomainEvent> getAllEvents(long fromVersion) {
        try {
            TypedQuery<EventEntity> query = entityManager.createQuery(
                    "SELECT e FROM EventEntity e WHERE e.version >= :fromVersion ORDER BY e.timestamp ASC, e.version ASC",
                    EventEntity.class
            );
            query.setParameter("fromVersion", fromVersion);

            List<EventEntity> entities = query.getResultList();

            return entities.stream()
                    .map(this::deserializeEvent)
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load events from version: " + fromVersion, e);
        }
    }

    private String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert DomainEvent to JSON: {}", event, e);
            throw new RuntimeException("Failed to convert DomainEvent to JSON: " + event, e);
        }
    }

    private void validateOptimisticConcurrency(String streamId, long expectedVersion) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT MAX(e.version) FROM EventEntity e WHERE e.streamId = :streamId",
                Long.class
        );
        query.setParameter("streamId", streamId);

        Long currentVersion = query.getSingleResult();
        if (currentVersion == null) {
            currentVersion = 0L;
        }

        if (currentVersion != expectedVersion) {
            throw new OptimisticConcurrencyException(
                    "Expected version " + expectedVersion + " but current version is " + currentVersion
            );
        }
    }

    private DomainEvent deserializeEvent(EventEntity entity) {
        try {
            return objectMapper.readValue(entity.getEventData(), DomainEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + entity.getEventId(), e);
        }
    }

    @Override
    public <T extends AggregateRoot> Optional<T> loadFromLatestSnapshot(
            String aggregateId, Class<T> aggregateClass) {
        try {
            // First try to load from snapshot
            var snapshotAggregate = snapshotService.restoreFromLatestSnapshot(aggregateId, aggregateClass);

            if (snapshotAggregate.isPresent()) {
                var aggregate = snapshotAggregate.get();
                // Apply any events that occurred after the snapshot
                var events = getEvents(aggregateId, aggregate.getVersion());
                aggregate.loadFromHistory(events);
                return Optional.of(aggregate);
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to load aggregate from snapshot: {}", aggregateId, e);
            throw new EventStoreException("Failed to load aggregate from snapshot: " + aggregateId, e);
        }
    }

    @Override
    public boolean createSnapshotIfNeeded(AggregateRoot aggregate) {
        try {
            return snapshotService.createSnapshotIfNeeded(aggregate);
        } catch (Exception e) {
            log.error("Failed to create snapshot for aggregate: {}", aggregate.getId(), e);
            throw new EventStoreException("Failed to create snapshot for aggregate: " + aggregate.getId(), e);
        }
    }

    @Override
    public void createSnapshot(AggregateRoot aggregate) {
        try {
            snapshotService.createSnapshot(aggregate);
        } catch (Exception e) {
            log.error("Failed to create snapshot for aggregate: {}", aggregate.getId(), e);
            throw new EventStoreException("Failed to create snapshot for aggregate: " + aggregate.getId(), e);
        }
    }

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(String message) {
            super(message);
        }
    }
}
