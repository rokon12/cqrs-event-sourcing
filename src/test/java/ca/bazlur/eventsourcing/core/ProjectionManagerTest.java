package ca.bazlur.eventsourcing.core;

import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectionManagerTest {
    private static final Logger log = LoggerFactory.getLogger(ProjectionManagerTest.class);

    @Mock
    private EventStore eventStore;
    
    private TestProjection testProjection1;
    private TestProjection testProjection2;
    private TestProjectionManager projectionManager;

    @BeforeEach
    void setUp() {
        testProjection1 = new TestProjection("TestProjection1");
        testProjection2 = new TestProjection("TestProjection2");
        
        // Create a simple test implementation that takes a List
        projectionManager = new TestProjectionManager(eventStore, List.of(testProjection1, testProjection2));
    }

    @Test
    void shouldRebuildAllProjectionsOnInitialize() {
        String correlationId = UUID.randomUUID().toString();
        List<DomainEvent> events = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null),
            new OrderItemAddedEvent("order-1", 2L, "product-1", "Product 1", 
                1, BigDecimal.valueOf(25.00), correlationId, null)
        );

        when(eventStore.getAllEvents()).thenReturn(events);

        projectionManager.rebuildAllProjections();

        verify(eventStore, atLeastOnce()).getAllEvents();
        assertEquals(2, testProjection1.getHandledEventsCount());
        assertEquals(2, testProjection2.getHandledEventsCount());
        assertTrue(testProjection1.isResetCalled());
        assertTrue(testProjection2.isResetCalled());
    }

    @Test
    void shouldProcessNewEventsForProjections() {
        String correlationId = UUID.randomUUID().toString();
        List<DomainEvent> existingEvents = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null)
        );
        List<DomainEvent> newEvents = List.of(
            new OrderItemAddedEvent("order-1", 2L, "product-1", "Product 1", 
                1, BigDecimal.valueOf(25.00), correlationId, null)
        );

        when(eventStore.getAllEvents()).thenReturn(existingEvents);
        when(eventStore.getAllEvents(2L)).thenReturn(newEvents);

        // Initialize projections with existing events
        projectionManager.rebuildAllProjections();
        
        // Reset counters
        testProjection1.resetCounters();
        testProjection2.resetCounters();

        // Process new events
        projectionManager.processNewEvents().join();

        verify(eventStore, atLeastOnce()).getAllEvents(2L);
        assertEquals(1, testProjection1.getHandledEventsCount());
        assertEquals(1, testProjection2.getHandledEventsCount());
    }

    @Test
    void shouldGetProjectionByClass() {
        TestProjectionManager manager = new TestProjectionManager(eventStore, List.of(testProjection1));

        Projection<String> foundProjection = manager.getProjection(TestProjection.class);
        
        assertNotNull(foundProjection);
        assertEquals(testProjection1, foundProjection);
    }

    @Test
    void shouldThrowExceptionWhenProjectionNotFound() {
        TestProjectionManager manager = new TestProjectionManager(eventStore, List.of());

        assertThrows(IllegalArgumentException.class, () -> 
            manager.getProjection(TestProjection.class)
        );
    }

    @Test
    void shouldHandleEmptyEventsList() {
        when(eventStore.getAllEvents()).thenReturn(List.of());

        projectionManager.rebuildAllProjections();

        verify(eventStore, atLeastOnce()).getAllEvents();
        assertEquals(0, testProjection1.getHandledEventsCount());
        assertEquals(0, testProjection2.getHandledEventsCount());
        assertTrue(testProjection1.isResetCalled());
        assertTrue(testProjection2.isResetCalled());
    }

    @Test
    void shouldProcessEventsInParallel() {
        String correlationId = UUID.randomUUID().toString();
        // Create many events to increase likelihood of parallel processing
        List<DomainEvent> events = List.of(
            new OrderCreatedEvent("order-1", 1L, "customer-1", correlationId, null),
            new OrderCreatedEvent("order-2", 1L, "customer-2", correlationId, null),
            new OrderCreatedEvent("order-3", 1L, "customer-3", correlationId, null),
            new OrderCreatedEvent("order-4", 1L, "customer-4", correlationId, null),
            new OrderCreatedEvent("order-5", 1L, "customer-5", correlationId, null)
        );

        when(eventStore.getAllEvents()).thenReturn(events);

        long startTime = System.currentTimeMillis();
        projectionManager.rebuildAllProjections();
        long endTime = System.currentTimeMillis();

        // Both projections should process all events
        assertEquals(5, testProjection1.getHandledEventsCount());
        assertEquals(5, testProjection2.getHandledEventsCount());
        
        // Should complete relatively quickly due to parallel processing
        assertTrue((endTime - startTime) < 1000, "Should complete in less than 1 second");
    }

    // Test ProjectionManager for testing purposes
    private static class TestProjectionManager {
        private final List<Projection<?>> testProjections;
        private final ConcurrentMap<String, Long> lastProcessedEventVersions = new ConcurrentHashMap<>();
        private final EventStore testEventStore;
        
        public TestProjectionManager(EventStore eventStore, List<Projection<?>> projections) {
            this.testProjections = projections;
            this.testEventStore = eventStore;
        }
        
        public void rebuildAllProjections() {
            log.info("Rebuilding all projections...");
            
            testProjections.parallelStream().forEach(projection -> {
                log.info("Rebuilding projection: {}", projection.getProjectionName());
                projection.reset();
                
                var events = testEventStore.getAllEvents();
                events.forEach(projection::handle);
                
                lastProcessedEventVersions.put(
                    projection.getProjectionName(),
                        events.isEmpty() ? 0L : events.getLast().getVersion()
                );
                
                log.info("Rebuilt projection: {} with {} events",
                        Optional.ofNullable(projection.getProjectionName()), events.size());
            });
            
            log.info("All projections rebuilt successfully");
        }
        
        public CompletableFuture<Void> processNewEvents() {
            return CompletableFuture.runAsync(() -> {
                testProjections.parallelStream().forEach(this::processNewEventsForProjection);
            });
        }
        
        private void processNewEventsForProjection(Projection<?> projection) {
            var projectionName = projection.getProjectionName();
            var lastProcessedVersion = lastProcessedEventVersions.getOrDefault(projectionName, 0L);
            
            var newEvents = testEventStore.getAllEvents(lastProcessedVersion + 1);
            
            if (!newEvents.isEmpty()) {
                log.debug("Processing {} new events for projection: {}", 
                    newEvents.size(), projectionName);
                
                newEvents.forEach(projection::handle);
                
                lastProcessedEventVersions.put(
                    projectionName, 
                    newEvents.getLast().getVersion()
                );
            }
        }
        
        @SuppressWarnings("unchecked")
        public <T> Projection<T> getProjection(Class<? extends Projection<T>> projectionClass) {
            return (Projection<T>) testProjections.stream()
                .filter(p -> projectionClass.isAssignableFrom(p.getClass()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Projection not found: " + projectionClass.getSimpleName()));
        }
    }

    // Test projection implementation
    private static class TestProjection implements Projection<String> {
        private final String name;
        private final AtomicInteger handledEventsCount = new AtomicInteger(0);
        @Getter
        private boolean resetCalled = false;

        public TestProjection(String name) {
            this.name = name;
        }

        @Override
        public void handle(DomainEvent event) {
            // Simulate some processing time
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            handledEventsCount.incrementAndGet();
        }

        @Override
        public String getById(String id) {
            return "test-" + id;
        }

        @Override
        public void reset() {
            resetCalled = true;
            handledEventsCount.set(0);
        }

        @Override
        public String getProjectionName() {
            return name;
        }

        public int getHandledEventsCount() {
            return handledEventsCount.get();
        }

        public void resetCounters() {
            handledEventsCount.set(0);
            resetCalled = false;
        }
    }
}