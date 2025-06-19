package ca.bazlur.eventsourcing.core;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
public abstract class DomainEvent {
    private final String eventId = UUID.randomUUID().toString();
    private final Instant timestamp = Instant.now();
    private final String aggregateId;
    private final long version;
    private final String correlationId;
    private final String causationId;
    
    protected DomainEvent(String aggregateId, long version, String correlationId, String causationId) {
        this.aggregateId = aggregateId;
        this.version = version;
        this.correlationId = correlationId;
        this.causationId = causationId;
    }
    
    public String getEventId() { return eventId; }
    public Instant getTimestamp() { return timestamp; }
    public String getAggregateId() { return aggregateId; }
    public long getVersion() { return version; }
    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    
    public abstract String getEventType();
}