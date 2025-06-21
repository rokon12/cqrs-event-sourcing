package ca.bazlur.eventsourcing.infrastructure.snapshot;

import ca.bazlur.eventsourcing.core.AggregateRoot;
import ca.bazlur.eventsourcing.domain.order.Order;
import ca.bazlur.eventsourcing.infrastructure.SnapshotEntity;
import ca.bazlur.eventsourcing.infrastructure.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class SnapshotServiceTest {

    @Mock
    private SnapshotRepository snapshotRepository;

    @Mock
    private SnapshotStrategy snapshotStrategy;

    @Inject
    private ObjectMapper objectMapper;

    @Mock
    private ObjectMapper mockObjectMapper;

    private SnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        snapshotService = new SnapshotService(snapshotRepository, snapshotStrategy, objectMapper);
    }

    @Test
    void shouldCreateSnapshotWhenStrategyDeterminesItsNeeded() {
        // Given
        var orderId = UUID.randomUUID().toString();
        var order = new Order(orderId);
        order.getClass(); // Trigger initialization

        when(snapshotStrategy.shouldCreateSnapshot(
            eq(orderId), eq("Order"), eq(0L), isNull()))
            .thenReturn(true);

        // When
        var result = snapshotService.createSnapshotIfNeeded(order);

        // Then
        assertTrue(result);
        verify(snapshotRepository).save(any(SnapshotEntity.class));
    }

    @Test
    void shouldNotCreateSnapshotWhenStrategyDeterminesItsNotNeeded() {
        // Given
        var orderId = UUID.randomUUID().toString();
        var order = new Order(orderId);

        when(snapshotStrategy.shouldCreateSnapshot(
            eq(orderId), eq("Order"), eq(0L), isNull()))
            .thenReturn(false);

        // When
        var result = snapshotService.createSnapshotIfNeeded(order);

        // Then
        assertFalse(result);
        verify(snapshotRepository, never()).save(any(SnapshotEntity.class));
    }

    @Test
    void shouldRestoreAggregateFromSnapshot() throws Exception {
        // Given
        var orderId = UUID.randomUUID().toString();
        var order = new Order(orderId);
        var snapshot = new SnapshotEntity(orderId, "Order", 0L, 
            objectMapper.writeValueAsString(order));

        when(snapshotRepository.findLatestSnapshot(orderId, "Order"))
            .thenReturn(Optional.of(snapshot));

        // When
        var result = snapshotService.restoreFromLatestSnapshot(orderId, Order.class);

        // Then
        assertTrue(result.isPresent());
        assertEquals(orderId, result.get().getId());
    }

    @Test
    void shouldThrowExceptionWhenSnapshotSerializationFails() throws Exception {
        // Given
        var orderId = UUID.randomUUID().toString();
        var order = new Order(orderId);
        
        // Create a service with a mock ObjectMapper that throws an exception
        var snapshotServiceWithMockMapper = new SnapshotService(snapshotRepository, snapshotStrategy, mockObjectMapper);
        
        when(mockObjectMapper.writeValueAsString(any()))
            .thenThrow(new RuntimeException("JSON serialization error"));

        // When/Then
        assertThrows(SnapshotCreationException.class, () ->
            snapshotServiceWithMockMapper.createSnapshot(order));
    }

    @Test
    void shouldReturnEmptyWhenNoSnapshotExists() {
        // Given
        var orderId = UUID.randomUUID().toString();
        when(snapshotRepository.findLatestSnapshot(orderId, "Order"))
            .thenReturn(Optional.empty());

        // When
        var result = snapshotService.restoreFromLatestSnapshot(orderId, Order.class);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionWhenSnapshotDeserializationFails() {
        // Given
        var orderId = UUID.randomUUID().toString();
        var invalidSnapshot = new SnapshotEntity(orderId, "Order", 0L, "invalid json");

        when(snapshotRepository.findLatestSnapshot(orderId, "Order"))
            .thenReturn(Optional.of(invalidSnapshot));

        // When/Then
        assertThrows(SnapshotRestorationException.class, () ->
            snapshotService.restoreFromLatestSnapshot(orderId, Order.class));
    }
}
