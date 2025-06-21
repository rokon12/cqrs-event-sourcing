package ca.bazlur.eventsourcing.core;

import java.util.Optional;

/**
 * Extension of EventStore that adds support for snapshot-based event sourcing.
 * This interface provides methods for loading and storing aggregate state snapshots
 * to optimize aggregate reconstruction.
 *
 * Snapshots are automatically created during event appending when the snapshot strategy
 * determines it's appropriate (e.g., after a certain number of events). This happens
 * in the appendEvents method.
 *
 * The interface also provides methods for manual snapshot management when needed:
 * - createSnapshotIfNeeded: Checks the strategy and creates a snapshot if needed
 * - createSnapshot: Forces immediate snapshot creation regardless of the strategy
 *
 * Usage patterns:
 * 1. Normal operation: Snapshots are created automatically during event appending
 * 2. Manual check: Call createSnapshotIfNeeded when you want to check if a snapshot is needed
 * 3. Forced snapshot: Call createSnapshot when you want to create a snapshot immediately
 */
public interface SnapshotEventStore extends EventStore {

    /**
     * Loads an aggregate from its latest snapshot and applies any newer events.
     *
     * @param aggregateId The ID of the aggregate to load
     * @param aggregateClass The class of the aggregate
     * @return The reconstructed aggregate, or empty if not found
     * @throws EventStoreException if there's an error loading the aggregate
     */
    <T extends AggregateRoot> Optional<T> loadFromLatestSnapshot(String aggregateId, Class<T> aggregateClass);

    /**
     * Creates a snapshot of the current aggregate state if needed according to the snapshot strategy.
     *
     * @param aggregate The aggregate to potentially snapshot
     * @return true if a snapshot was created, false otherwise
     * @throws EventStoreException if there's an error creating the snapshot
     */
    boolean createSnapshotIfNeeded(AggregateRoot aggregate);

    /**
     * Forces creation of a snapshot for the given aggregate, regardless of the snapshot strategy.
     *
     * @param aggregate The aggregate to snapshot
     * @throws EventStoreException if there's an error creating the snapshot
     */
    void createSnapshot(AggregateRoot aggregate);
}
