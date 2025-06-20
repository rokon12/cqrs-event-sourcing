package ca.bazlur.eventsourcing.config;

import ca.bazlur.eventsourcing.core.EventSchemaManager;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TestEventRegistryConfig {
    private static final Logger log = LoggerFactory.getLogger(TestEventRegistryConfig.class);

    @Inject
    EventSchemaManager schemaManager;

    void onStart(@Observes StartupEvent evt) {
        log.info("Registering event types for tests...");
        
        try {
            // Register all event types used in tests
            schemaManager.registerEventType(OrderCreatedEvent.class);
            schemaManager.registerEventType(OrderItemAddedEvent.class);
            
            log.info("Test event types registered successfully");
        } catch (Exception e) {
            log.error("Failed to register test event types", e);
            throw e;
        }
    }
}