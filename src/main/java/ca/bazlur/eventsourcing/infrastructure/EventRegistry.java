package ca.bazlur.eventsourcing.infrastructure;

import ca.bazlur.eventsourcing.core.EventSchemaManager;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class EventRegistry {
    private static final Logger log = LoggerFactory.getLogger(EventRegistry.class);
    private final EventSchemaManager schemaManager;

    @Inject
    public EventRegistry(EventSchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    void onStart(@Observes StartupEvent evt) {
        log.info("Starting event type registration...");

        try {
            // Order events
            log.debug("Registering OrderCreatedEvent...");
            schemaManager.registerEventType(OrderCreatedEvent.class);
            log.debug("Successfully registered OrderCreatedEvent");

            log.debug("Registering OrderItemAddedEvent...");
            schemaManager.registerEventType(OrderItemAddedEvent.class);
            log.debug("Successfully registered OrderItemAddedEvent");

            // Add other event types here as they are created

            log.info("All event types registered successfully");
        } catch (Exception e) {
            log.error("Failed to register event types: {}", e.getMessage(), e);
            throw e;
        }
    }
}
