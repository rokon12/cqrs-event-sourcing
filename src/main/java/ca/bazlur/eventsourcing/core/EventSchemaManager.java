package ca.bazlur.eventsourcing.core;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class EventSchemaManager {
    private static final Logger log = LoggerFactory.getLogger(EventSchemaManager.class);
    private final Map<String, Integer> currentVersions = new HashMap<>();

    public void registerEventType(Class<? extends DomainEvent> eventClass) {
        log.debug("Registering event type for class: {}", eventClass.getName());

        var schemaVersion = eventClass.getAnnotation(EventSchemaVersion.class);
        if (schemaVersion == null) {
            throw new EventSchemaException("Event class " + eventClass.getName() + " must be annotated with @EventSchemaVersion");
        }

        var eventType = getEventType(eventClass);
        var version = schemaVersion.value();

        log.debug("Derived event type '{}' from class {}", eventType, eventClass.getSimpleName());

        currentVersions.compute(eventType, (type, currentVersion) -> {
            if (currentVersion != null && currentVersion > version) {
                log.warn("Registering older version {} for event type {}. Current version is {}", 
                    version, eventType, currentVersion);
                return currentVersion;
            }
            return version;
        });

        log.info("Registered event type {} with schema version {}", eventType, version);
    }

    public void validateEvent(DomainEvent event) {
        log.debug("Validating event: {} of type {}", event.getClass().getSimpleName(), event.getEventType());

        var eventClass = event.getClass();
        var schemaVersion = eventClass.getAnnotation(EventSchemaVersion.class);
        if (schemaVersion == null) {
            throw new EventSchemaException("Event " + eventClass.getName() + " must be annotated with @EventSchemaVersion");
        }

        var expectedEventType = getEventType(eventClass);
        var actualEventType = event.getEventType();

        if (!expectedEventType.equals(actualEventType)) {
            throw new EventSchemaException(
                String.format("Event type mismatch. Expected '%s' but got '%s' for class %s",
                    expectedEventType, actualEventType, eventClass.getSimpleName()));
        }

        var currentVersion = currentVersions.get(actualEventType);
        if (currentVersion == null) {
            throw new EventSchemaException("Unknown event type: " + actualEventType);
        }

        if (schemaVersion.value() < currentVersion) {
            throw new EventSchemaException(
                String.format("Event %s version %d is older than current version %d", 
                    actualEventType, schemaVersion.value(), currentVersion));
        }
    }

    private String getEventType(Class<? extends DomainEvent> eventClass) {
        String className = eventClass.getSimpleName();
        if (!className.endsWith("Event")) {
            throw new EventSchemaException("Event class name must end with 'Event': " + className);
        }
        return className.substring(0, className.length() - "Event".length());
    }
}
