package ca.bazlur.eventsourcing.infrastructure;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.EventStore;
import ca.bazlur.eventsourcing.core.EventStoreException;
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

@ApplicationScoped
public class JpaEventStore implements EventStore {

    private static final Logger log = LoggerFactory.getLogger(JpaEventStore.class);

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Inject
    public JpaEventStore(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void appendEvents(String streamId, List<DomainEvent> events, long expectedVersion) {
        validateOptimisticConcurrency(streamId, expectedVersion);

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

        log.debug("Appended {} events to stream {}", events.size(), streamId);
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

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(String message) {
            super(message);
        }
    }
}
