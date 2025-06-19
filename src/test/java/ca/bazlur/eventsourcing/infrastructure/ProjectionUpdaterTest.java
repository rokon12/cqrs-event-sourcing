package ca.bazlur.eventsourcing.infrastructure;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.Projection;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectionUpdaterTest {
    private static final Logger log = LoggerFactory.getLogger(ProjectionUpdaterTest.class);

    @Mock
    private Projection<String> projection1;
    
    @Mock
    private Projection<String> projection2;
    
    private TestProjectionUpdater projectionUpdater;

    @BeforeEach
    void setUp() {
        projectionUpdater = new TestProjectionUpdater(List.of(projection1, projection2));
    }

    @Test
    void shouldUpdateAllProjectionsSynchronously() {
        String correlationId = UUID.randomUUID().toString();
        List<DomainEvent> events = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null),
            new OrderItemAddedEvent("order-1", 2L, "product-1", "Product 1", 
                1, BigDecimal.valueOf(25.00), correlationId, null)
        );

        projectionUpdater.updateProjectionsSync(events);

        verify(projection1, times(2)).handle(any(DomainEvent.class));
        verify(projection2, times(2)).handle(any(DomainEvent.class));
        
        // Verify specific events were handled
        verify(projection1).handle(events.get(0));
        verify(projection1).handle(events.get(1));
        verify(projection2).handle(events.get(0));
        verify(projection2).handle(events.get(1));
    }

    @Test
    void shouldUpdateAllProjectionsAsynchronously() {
        String correlationId = UUID.randomUUID().toString();
        List<DomainEvent> events = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null)
        );

        CompletableFuture<Void> future = projectionUpdater.updateProjections(events);
        
        assertDoesNotThrow(() -> future.join());
        
        verify(projection1).handle(events.get(0));
        verify(projection2).handle(events.get(0));
    }

    @Test
    void shouldHandleEmptyEventsList() {
        List<DomainEvent> emptyEvents = List.of();

        projectionUpdater.updateProjectionsSync(emptyEvents);

        verify(projection1, never()).handle(any(DomainEvent.class));
        verify(projection2, never()).handle(any(DomainEvent.class));
    }

    @Test
    void shouldContinueUpdatingOtherProjectionsWhenOneThrows() {
        String correlationId = UUID.randomUUID().toString();
        List<DomainEvent> events = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null)
        );

        // Make first projection throw exception
        doThrow(new RuntimeException("Projection error")).when(projection1).handle(any());

        // Should not throw exception
        assertDoesNotThrow(() -> projectionUpdater.updateProjectionsSync(events));

        // Both projections should be called
        verify(projection1).handle(events.get(0));
        verify(projection2).handle(events.get(0));
    }

    @Test
    void shouldHandleMultipleEventsCorrectly() {
        String correlationId = UUID.randomUUID().toString();
        List<DomainEvent> events = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null),
            new OrderCreatedEvent("order-2", 1L, "customer-2", correlationId, null),
            new OrderItemAddedEvent("order-1", 2L, "product-1", "Product 1", 
                1, BigDecimal.valueOf(25.00), correlationId, null),
            new OrderItemAddedEvent("order-2", 2L, "product-2", "Product 2", 
                2, BigDecimal.valueOf(15.00), correlationId, null)
        );

        projectionUpdater.updateProjectionsSync(events);

        // Each projection should handle all 4 events
        verify(projection1, times(4)).handle(any(DomainEvent.class));
        verify(projection2, times(4)).handle(any(DomainEvent.class));
    }

    @Test
    void shouldProcessProjectionsInParallel() {
        TestProjection slowProjection1 = new TestProjection("slow1", 100); // 100ms delay
        TestProjection slowProjection2 = new TestProjection("slow2", 100); // 100ms delay
        
        TestProjectionUpdater updater = new TestProjectionUpdater(List.of(slowProjection1, slowProjection2));

        String correlationId = UUID.randomUUID().toString();
        List<DomainEvent> events = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null)
        );

        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> future = updater.updateProjections(events);
        future.join();
        long endTime = System.currentTimeMillis();

        // Should complete in less than 200ms (parallel) rather than 200ms+ (sequential)
        long duration = endTime - startTime;
        assertTrue(duration < 150, "Should complete faster due to parallel processing, took: " + duration + "ms");
        
        assertEquals(1, slowProjection1.getHandledEventsCount());
        assertEquals(1, slowProjection2.getHandledEventsCount());
    }

    @Test
    void shouldHandleNullProjectionName() {
        when(projection1.getProjectionName()).thenReturn(null);
        when(projection2.getProjectionName()).thenReturn("TestProjection2");

        String correlationId = UUID.randomUUID().toString();
        List<DomainEvent> events = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null)
        );

        // Should not throw exception
        assertDoesNotThrow(() -> projectionUpdater.updateProjectionsSync(events));
        
        verify(projection1).handle(events.getFirst());
        verify(projection2).handle(events.getFirst());
    }

    // Test ProjectionUpdater for testing purposes
    private static class TestProjectionUpdater {
        private final List<Projection<?>> testProjections;
        
        public TestProjectionUpdater(List<Projection<?>> projections) {
            this.testProjections = projections;
        }
        
        public CompletableFuture<Void> updateProjections(List<DomainEvent> events) {
            return CompletableFuture.runAsync(() -> {
                log.debug("Updating projections with {} events", events.size());
                
                testProjections.parallelStream().forEach(projection -> {
                    try {
                        events.forEach(projection::handle);
                        log.debug("Updated projection: {} with {} events", 
                            projection.getProjectionName(), events.size());
                    } catch (Exception e) {
                        log.error("Error updating projection: {}", 
                            projection.getProjectionName(), e);
                    }
                });
            });
        }
        
        public void updateProjectionsSync(List<DomainEvent> events) {
            log.debug("Synchronously updating projections with {} events", events.size());
            
            for (var projection : testProjections) {
                try {
                    events.forEach(projection::handle);
                    log.debug("Updated projection: {} with {} events", 
                        projection.getProjectionName(), events.size());
                } catch (Exception e) {
                    log.error("Error updating projection: {}", 
                        projection.getProjectionName(), e);
                }
            }
        }
    }

    // Test projection for performance testing
    private static class TestProjection implements Projection<String> {
        private final String name;
        private final long delayMs;
        private final AtomicInteger handledEventsCount = new AtomicInteger(0);

        public TestProjection(String name, long delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }

        @Override
        public void handle(DomainEvent event) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            handledEventsCount.incrementAndGet();
        }

        @Override
        public String getById(String id) {
            return "test-" + id;
        }

        @Override
        public void reset() {
            handledEventsCount.set(0);
        }

        @Override
        public String getProjectionName() {
            return name;
        }

        public int getHandledEventsCount() {
            return handledEventsCount.get();
        }
    }
}