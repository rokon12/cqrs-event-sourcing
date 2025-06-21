package ca.bazlur.eventsourcing.infrastructure.snapshot;

import ca.bazlur.eventsourcing.core.AggregateRoot;
import ca.bazlur.eventsourcing.infrastructure.SnapshotEntity;
import ca.bazlur.eventsourcing.infrastructure.SnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Service responsible for creating and restoring aggregate snapshots.
 */
@ApplicationScoped
public class SnapshotService {
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private final SnapshotRepository snapshotRepository;
    private final SnapshotStrategy snapshotStrategy;
    private final ObjectMapper objectMapper;

    public SnapshotService(SnapshotRepository snapshotRepository,
                          SnapshotStrategy snapshotStrategy,
                          ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotStrategy = snapshotStrategy;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a snapshot of the aggregate if the strategy determines one is needed.
     *
     * @param aggregate The aggregate to snapshot
     * @return true if a snapshot was created, false otherwise
     */
    @Transactional
    public boolean createSnapshotIfNeeded(AggregateRoot aggregate) {
        try {
            var aggregateId = aggregate.getId();
            var aggregateType = aggregate.getClass().getSimpleName();
            var currentVersion = aggregate.getVersion();

            var lastSnapshotVersion = snapshotRepository
                .findLatestSnapshotVersion(aggregateId, aggregateType)
                .orElse(null);

            if (snapshotStrategy.shouldCreateSnapshot(
                    aggregateId, aggregateType, currentVersion, lastSnapshotVersion)) {
                createSnapshot(aggregate);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to create snapshot for aggregate: {}", aggregate.getId(), e);
            return false;
        }
    }

    /**
     * Creates a snapshot of the current aggregate state.
     *
     * @param aggregate The aggregate to snapshot
     */
    @Transactional
    public void createSnapshot(AggregateRoot aggregate) {
        try {
            var aggregateId = aggregate.getId();
            var aggregateType = aggregate.getClass().getSimpleName();
            var version = aggregate.getVersion();
            var serializedState = objectMapper.writeValueAsString(aggregate);

            var snapshot = new SnapshotEntity(aggregateId, aggregateType, version, serializedState);
            snapshotRepository.save(snapshot);

            log.info("Created snapshot for aggregate: {} of type: {} at version: {}",
                aggregateId, aggregateType, version);
        } catch (Exception e) {
            log.error("Failed to create snapshot for aggregate: {}", aggregate.getId(), e);
            throw new SnapshotCreationException("Failed to create snapshot", e);
        }
    }

    /**
     * Restores an aggregate from its latest snapshot.
     *
     * @param aggregateId The ID of the aggregate to restore
     * @param aggregateClass The class of the aggregate
     * @return Optional containing the restored aggregate if a snapshot exists
     */
    public <T extends AggregateRoot> Optional<T> restoreFromLatestSnapshot(
            String aggregateId, Class<T> aggregateClass) {
        try {
            return snapshotRepository
                .findLatestSnapshot(aggregateId, aggregateClass.getSimpleName())
                .map(snapshot -> deserializeSnapshot(snapshot, aggregateClass));
        } catch (Exception e) {
            log.error("Failed to restore snapshot for aggregate: {}", aggregateId, e);
            throw new SnapshotRestorationException(
                "Failed to restore snapshot for aggregate: " + aggregateId, e);
        }
    }

    private <T extends AggregateRoot> T deserializeSnapshot(
            SnapshotEntity snapshot, Class<T> aggregateClass) {
        try {
            return objectMapper.readValue(snapshot.getStateData(), aggregateClass);
        } catch (Exception e) {
            log.error("Failed to deserialize snapshot for aggregate: {}", 
                snapshot.getAggregateId(), e);
            throw new SnapshotRestorationException(
                "Failed to deserialize snapshot for aggregate: " + snapshot.getAggregateId(), e);
        }
    }
}