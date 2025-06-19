package ca.bazlur.eventsourcing.infrastructure;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "events")
public class EventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_id", unique = true, nullable = false, length = 36)
    private String eventId;
    
    @Column(name = "stream_id", nullable = false)
    private String streamId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb", nullable = false)
    private String eventData;
    
    @Column(name = "version", nullable = false)
    private Long version;
    
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    @Column(name = "correlation_id", length = 36)
    private String correlationId;
    
    @Column(name = "causation_id", length = 36)
    private String causationId;
    
    // Default constructor for JPA
    public EventEntity() {}
    
    public EventEntity(String eventId, String streamId, String eventType, String eventData, 
                      Long version, Instant timestamp, String correlationId, String causationId) {
        this.eventId = eventId;
        this.streamId = streamId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.version = version;
        this.timestamp = timestamp;
        this.correlationId = correlationId;
        this.causationId = causationId;
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    
    public String getStreamId() { return streamId; }
    public void setStreamId(String streamId) { this.streamId = streamId; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public String getEventData() { return eventData; }
    public void setEventData(String eventData) { this.eventData = eventData; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    
    public String getCausationId() { return causationId; }
    public void setCausationId(String causationId) { this.causationId = causationId; }
}