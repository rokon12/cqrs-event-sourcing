package ca.bazlur.eventsourcing.infrastructure.snapshot;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of SnapshotStrategy that creates snapshots
 * after a configurable number of events.
 */
@ApplicationScoped
public class DefaultSnapshotStrategy implements SnapshotStrategy {
    private static final Logger log = LoggerFactory.getLogger(DefaultSnapshotStrategy.class);
    private static final int DEFAULT_SNAPSHOT_FREQUENCY = 100;

    private final int snapshotFrequency;

    public DefaultSnapshotStrategy(
            @ConfigProperty(name = "snapshot.frequency", defaultValue = "100") int snapshotFrequency) {
        if (snapshotFrequency < 0) {
            throw new IllegalArgumentException("Snapshot frequency cannot be negative");
        }
        this.snapshotFrequency = snapshotFrequency == 0 ? DEFAULT_SNAPSHOT_FREQUENCY : snapshotFrequency;
        log.info("Initialized DefaultSnapshotStrategy with frequency: {}", this.snapshotFrequency);
    }

    @Override
    public boolean shouldCreateSnapshot(String aggregateId, String aggregateType,
                                     long currentVersion, Long lastSnapshotVersion) {
        if (lastSnapshotVersion == null) {
            // Create first snapshot when we reach the frequency threshold
            boolean shouldCreate = currentVersion >= snapshotFrequency;
            if (shouldCreate) {
                log.debug("Creating first snapshot for aggregate: {} of type: {} at version: {}",
                    aggregateId, aggregateType, currentVersion);
            }
            return shouldCreate;
        }

        // Handle potential version reset or overflow
        if (currentVersion < lastSnapshotVersion) {
            log.warn("Current version {} is less than last snapshot version {} for aggregate: {}",
                currentVersion, lastSnapshotVersion, aggregateId);
            return true;
        }

        // Special handling for values near Long.MAX_VALUE to prevent overflow
        if (currentVersion == Long.MAX_VALUE || 
            (Long.MAX_VALUE - currentVersion) < snapshotFrequency) {
            log.debug("Creating snapshot due to version approaching Long.MAX_VALUE for aggregate: {}", 
                aggregateId);
            return true;
        }

        // Create new snapshot when we've accumulated enough events since the last snapshot
        long eventsSinceLastSnapshot = currentVersion - lastSnapshotVersion;
        boolean shouldCreate = eventsSinceLastSnapshot >= snapshotFrequency;

        if (shouldCreate) {
            log.debug("Creating new snapshot for aggregate: {} of type: {} at version: {}. Events since last snapshot: {}",
                aggregateId, aggregateType, currentVersion, eventsSinceLastSnapshot);
        }

        return shouldCreate;
    }

    @Override
    public int getSnapshotFrequency() {
        return snapshotFrequency;
    }
}
