package ca.bazlur.eventsourcing.infrastructure;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.EventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class JpaEventStore implements EventStore {

  private static final Logger log = LoggerFactory.getLogger(JpaEventStore.class);

  private final EntityManager entityManager;
  private final ObjectMapper objectMapper;
  private final ExecutorService virtualThreadExecutor;

  @Inject
  public JpaEventStore(EntityManager entityManager, ObjectMapper objectMapper) {
    this.entityManager = entityManager;
    this.objectMapper = objectMapper;
    this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public CompletableFuture<Void> appendEvents(String streamId, List<DomainEvent> events, long expectedVersion) {
    return CompletableFuture.runAsync(() -> appendEventsSync(streamId, events, expectedVersion), virtualThreadExecutor);
  }

  @Transactional
  public void appendEventsSync(String streamId, List<DomainEvent> events, long expectedVersion) {
    try {
      // Check optimistic concurrency
      validateOptimisticConcurrency(streamId, expectedVersion);

      // Persist events
      for (DomainEvent event : events) {
        EventEntity entity = new EventEntity(
            event.getEventId(),
            streamId,
            event.getEventType(),
            objectMapper.writeValueAsString(event),
            event.getVersion(),
            event.getTimestamp(),
            event.getCorrelationId(),
            event.getCausationId()
        );

        entityManager.persist(entity);
      }

      entityManager.flush();

      log.debug("Appended {} events to stream {} (JPA)", events.size(), streamId);

    } catch (Exception e) {
      throw new RuntimeException("Failed to append events to stream: " + streamId, e);
    }
  }

  @Override
  public CompletableFuture<List<DomainEvent>> getEvents(String streamId) {
    return CompletableFuture.supplyAsync(() -> getEventsSync(streamId), virtualThreadExecutor);
  }

  @Transactional
  public List<DomainEvent> getEventsSync(String streamId) {
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
  public CompletableFuture<List<DomainEvent>> getEvents(String streamId, long fromVersion) {
    return CompletableFuture.supplyAsync(() -> {
      return getEventsFromVersionSync(streamId, fromVersion);
    }, virtualThreadExecutor);
  }

  @Transactional
  public List<DomainEvent> getEventsFromVersionSync(String streamId, long fromVersion) {
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
  public CompletableFuture<List<DomainEvent>> getAllEvents() {
    return CompletableFuture.supplyAsync(() -> {
      return getAllEventsSync();
    }, virtualThreadExecutor);
  }

  @Transactional
  public List<DomainEvent> getAllEventsSync() {
    try {
      TypedQuery<EventEntity> query = entityManager.createQuery(
          "SELECT e FROM EventEntity e ORDER BY e.timestamp ASC, e.version ASC",
          EventEntity.class
      );

      List<EventEntity> entities = query.getResultList();

      return entities.stream()
          .map(this::deserializeEvent)
          .toList();

    } catch (Exception e) {
      throw new RuntimeException("Failed to load all events", e);
    }
  }

  @Override
  public CompletableFuture<List<DomainEvent>> getAllEvents(long fromVersion) {
    return CompletableFuture.supplyAsync(() -> {
      return getAllEventsFromVersionSync(fromVersion);
    }, virtualThreadExecutor);
  }

  @Transactional
  public List<DomainEvent> getAllEventsFromVersionSync(long fromVersion) {
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