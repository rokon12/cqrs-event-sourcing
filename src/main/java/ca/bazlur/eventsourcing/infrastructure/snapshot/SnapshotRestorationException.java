package ca.bazlur.eventsourcing.infrastructure.snapshot;

/**
 * Exception thrown when there are issues restoring an aggregate from a snapshot.
 */
public class SnapshotRestorationException extends RuntimeException {
    public SnapshotRestorationException(String message) {
        super(message);
    }

    public SnapshotRestorationException(String message, Throwable cause) {
        super(message, cause);
    }
}