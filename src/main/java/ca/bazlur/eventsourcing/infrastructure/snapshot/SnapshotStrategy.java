package ca.bazlur.eventsourcing.infrastructure.snapshot;

/**
 * Strategy interface for determining when to create snapshots of aggregates.
 * Implementations can define different policies for snapshot creation
 * (e.g., every N events, time-based, etc.).
 */
public interface SnapshotStrategy {
    /**
     * Determines whether a snapshot should be created for the given aggregate.
     *
     * @param aggregateId The ID of the aggregate
     * @param aggregateType The type of the aggregate
     * @param currentVersion The current version of the aggregate
     * @param lastSnapshotVersion The version of the last snapshot (if any)
     * @return true if a snapshot should be created, false otherwise
     */
    boolean shouldCreateSnapshot(String aggregateId, String aggregateType, 
                               long currentVersion, Long lastSnapshotVersion);

    /**
     * Gets the frequency at which snapshots should be created (e.g., every N events).
     * This can be used for logging and monitoring purposes.
     *
     * @return the snapshot frequency
     */
    int getSnapshotFrequency();
}