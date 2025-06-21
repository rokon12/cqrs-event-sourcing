package ca.bazlur.eventsourcing.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "snapshots",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"aggregate_id", "aggregate_type"},
        name = "uk_snapshots_aggregate"))
public class SnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "state_data", nullable = false, columnDefinition = "jsonb")
    private String stateData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SnapshotEntity() {
    }

    public SnapshotEntity(String aggregateId, String aggregateType, long version, String stateData) {
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.version = version;
        this.stateData = stateData;
        this.createdAt = Instant.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getStateData() {
        return stateData;
    }

    public void setStateData(String stateData) {
        this.stateData = stateData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}