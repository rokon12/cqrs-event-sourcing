package ca.bazlur.eventsourcing.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Repository for managing aggregate snapshots using Panache.
 * Provides operations for storing and retrieving aggregate state snapshots.
 */
@ApplicationScoped
public class SnapshotRepository implements PanacheRepository<SnapshotEntity> {
    private static final Logger log = LoggerFactory.getLogger(SnapshotRepository.class);

    @Transactional
    public void save(SnapshotEntity snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot must not be null");
        }
        try {
            persist(snapshot);
            log.debug("Saved snapshot for aggregate: {} of type: {} at version: {}", 
                snapshot.getAggregateId(), snapshot.getAggregateType(), snapshot.getVersion());
        } catch (PersistenceException e) {
            log.error("Failed to save snapshot for aggregate: {}", snapshot.getAggregateId(), e);
            throw new SnapshotPersistenceException(
                "Failed to save snapshot: " + snapshot.getAggregateId(), e);
        }
    }

    public Optional<SnapshotEntity> findLatestSnapshot(String aggregateId, String aggregateType) {
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("Aggregate ID must not be null or blank");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("Aggregate type must not be null or blank");
        }

        try {
            return find("aggregateId = ?1 and aggregateType = ?2 order by version desc", 
                    aggregateId, aggregateType)
                .firstResultOptional();
        } catch (PersistenceException e) {
            log.error("Failed to find latest snapshot for aggregate: {} of type: {}", 
                aggregateId, aggregateType, e);
            throw new SnapshotPersistenceException(
                "Failed to find latest snapshot for aggregate: " + aggregateId, e);
        }
    }

    public Optional<Long> findLatestSnapshotVersion(String aggregateId, String aggregateType) {
        return findLatestSnapshot(aggregateId, aggregateType)
            .map(SnapshotEntity::getVersion);
    }

    @Transactional
    public void deleteByAggregateIdAndAggregateType(String aggregateId, String aggregateType) {
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("Aggregate ID must not be null or blank");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("Aggregate type must not be null or blank");
        }

        try {
            delete("aggregateId = ?1 and aggregateType = ?2", aggregateId, aggregateType);
            log.debug("Deleted snapshots for aggregate: {} of type: {}", aggregateId, aggregateType);
        } catch (PersistenceException e) {
            log.error("Failed to delete snapshots for aggregate: {} of type: {}", 
                aggregateId, aggregateType, e);
            throw new SnapshotPersistenceException(
                "Failed to delete snapshots for aggregate: " + aggregateId, e);
        }
    }
}
