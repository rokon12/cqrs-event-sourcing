package ca.bazlur.eventsourcing.infrastructure;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.EventSchemaManager;
import ca.bazlur.eventsourcing.core.EventStoreException;
import ca.bazlur.eventsourcing.domain.order.Order;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.infrastructure.snapshot.SnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class JpaEventStoreTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EventSchemaManager schemaManager;

    @Mock
    private SnapshotService snapshotService;

    @Mock
    private TypedQuery<Long> longQuery;

    @Mock
    private TypedQuery<EventEntity> eventQuery;

    private JpaEventStore eventStore;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventStore = new JpaEventStore(entityManager, objectMapper, schemaManager, snapshotService);
    }

    @Test
    void shouldLoadAggregateFromSnapshot() {
        // Given
        var orderId = UUID.randomUUID().toString();
        var order = new Order(orderId);
        var event = new OrderCreatedEvent(orderId, 1L, "customer-1", "correlation", "causation");

        when(snapshotService.restoreFromLatestSnapshot(orderId, Order.class))
            .thenReturn(Optional.of(order));
        when(entityManager.createQuery(anyString(), eq(EventEntity.class)))
            .thenReturn(mock(jakarta.persistence.TypedQuery.class));

        // When
        var result = eventStore.loadFromLatestSnapshot(orderId, Order.class);

        // Then
        assertTrue(result.isPresent());
        assertEquals(orderId, result.get().getId());
        verify(snapshotService).restoreFromLatestSnapshot(orderId, Order.class);
    }

    @Test
    void shouldCreateSnapshotWhenAppendingEvents() throws Exception {
        // Given
        var orderId = UUID.randomUUID().toString();
        var event = new OrderCreatedEvent(orderId, 1L, "customer-1", "correlation", "causation");

        // Mock optimistic concurrency check
        when(entityManager.createQuery("SELECT MAX(e.version) FROM EventEntity e WHERE e.streamId = :streamId", Long.class))
            .thenReturn(longQuery);
        when(longQuery.setParameter("streamId", orderId)).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(0L); // Expected version

        // Mock schema validation (no exception thrown)
        doNothing().when(schemaManager).validateEvent(any());

        // Mock JSON serialization
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventData\": \"test\"}");

        // Mock event loading for snapshot creation
        when(entityManager.createQuery("SELECT e FROM EventEntity e WHERE e.streamId = :streamId ORDER BY e.version ASC", EventEntity.class))
            .thenReturn(eventQuery);
        when(eventQuery.setParameter("streamId", orderId)).thenReturn(eventQuery);
        when(eventQuery.getResultList()).thenReturn(List.of());

        // When
        eventStore.appendEvents(orderId, List.of(event), 0L);

        // Then
        verify(entityManager, times(1)).persist(any(EventEntity.class));
        verify(entityManager).flush();
    }

    @Test
    void shouldHandleOptimisticConcurrencyWithSnapshots() {
        // Given
        var orderId = UUID.randomUUID().toString();
        var event = new OrderCreatedEvent(orderId, 2L, "customer-1", "correlation", "causation");

        // Mock optimistic concurrency check to return version 1 (conflict with expected 0)
        when(entityManager.createQuery("SELECT MAX(e.version) FROM EventEntity e WHERE e.streamId = :streamId", Long.class))
            .thenReturn(longQuery);
        when(longQuery.setParameter("streamId", orderId)).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(1L); // Current version is 1, expected is 0

        // When/Then
        assertThrows(EventStoreException.class, () ->
            eventStore.appendEvents(orderId, List.of(event), 0L));
    }

    @Test
    void shouldReturnEmptyWhenNoSnapshotExists() {
        // Given
        var orderId = UUID.randomUUID().toString();
        when(snapshotService.restoreFromLatestSnapshot(orderId, Order.class))
            .thenReturn(Optional.empty());

        // When
        var result = eventStore.loadFromLatestSnapshot(orderId, Order.class);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldLoadEventsAfterSnapshot() throws Exception {
        // Given
        var orderId = UUID.randomUUID().toString();
        var order = new Order(orderId);
        var snapshotVersion = 5L;
        
        // Create a proper order with initial state
        var initialEvent = new OrderCreatedEvent(orderId, snapshotVersion, 
            "customer-1", "correlation", "causation");
        order.loadFromHistory(List.of(initialEvent));

        when(snapshotService.restoreFromLatestSnapshot(orderId, Order.class))
            .thenReturn(Optional.of(order));

        // Mock loading events after snapshot - return empty list for simplicity
        when(entityManager.createQuery("SELECT e FROM EventEntity e WHERE e.streamId = :streamId AND e.version >= :fromVersion ORDER BY e.version ASC", EventEntity.class))
            .thenReturn(eventQuery);
        when(eventQuery.setParameter("streamId", orderId)).thenReturn(eventQuery);
        when(eventQuery.setParameter("fromVersion", snapshotVersion)).thenReturn(eventQuery);
        when(eventQuery.getResultList()).thenReturn(List.of()); // No new events after snapshot

        // When
        var result = eventStore.loadFromLatestSnapshot(orderId, Order.class);

        // Then
        assertTrue(result.isPresent());
        assertEquals(snapshotVersion, result.get().getVersion());
        verify(snapshotService).restoreFromLatestSnapshot(orderId, Order.class);
    }
}
