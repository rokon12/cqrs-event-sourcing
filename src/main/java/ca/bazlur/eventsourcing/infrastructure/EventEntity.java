package ca.bazlur.eventsourcing.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Setter
@Getter
@Entity
@Table(name = "events",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"stream_id", "version"},
        name = "uk_events_stream_version"))
public class EventEntity {

    // Getters and setters
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

}
