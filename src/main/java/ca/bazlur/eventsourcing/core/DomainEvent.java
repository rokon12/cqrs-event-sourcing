package ca.bazlur.eventsourcing.core;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
public abstract class DomainEvent {
    private final String eventId = UUID.randomUUID().toString();
    private final Instant timestamp = Instant.now();
    private final String aggregateId;
    private final long version;
    private final String correlationId;
    private final String causationId;
    private final int schemaVersion;

    protected DomainEvent(String aggregateId, long version, String correlationId, String causationId) {
        this.aggregateId = aggregateId;
        this.version = version;
        this.correlationId = correlationId;
        this.causationId = causationId;

        var annotation = this.getClass().getAnnotation(EventSchemaVersion.class);
        if (annotation == null) {
            throw new EventSchemaException("Event class " + this.getClass().getName() 
                + " must be annotated with @EventSchemaVersion");
        }
        this.schemaVersion = annotation.value();
    }

    /**
     * Gets the event type name. By default, returns the class name without the "Event" suffix.
     * Override this method if you need a different naming convention.
     *
     * @return the event type name
     */
    public String getEventType() {
        return this.getClass().getSimpleName().replaceAll("Event$", "");
    }

    /**
     * Evolve this event to the target schema version.
     * Override this method to provide custom evolution logic.
     *
     * @param targetVersion the target schema version
     * @return evolved event instance
     * @throws EventSchemaException if evolution to target version is not supported
     */
    public DomainEvent evolve(int targetVersion) {
        if (targetVersion == this.schemaVersion) {
            return this;
        }
        throw new EventSchemaException(String.format(
            "Evolution from version %d to %d is not supported for event type %s",
            this.schemaVersion, targetVersion, getEventType()));
    }

    /**
     * Check if this event can be evolved to the target schema version.
     *
     * @param targetVersion the target schema version
     * @return true if evolution is supported, false otherwise
     */
    public boolean canEvolve(int targetVersion) {
        try {
            evolve(targetVersion);
            return true;
        } catch (EventSchemaException e) {
            return false;
        }
    }
}
