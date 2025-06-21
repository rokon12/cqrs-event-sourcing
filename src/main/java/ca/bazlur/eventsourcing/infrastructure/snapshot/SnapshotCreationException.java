package ca.bazlur.eventsourcing.infrastructure.snapshot;

/**
 * Exception thrown when there are issues creating a snapshot.
 */
public class SnapshotCreationException extends RuntimeException {
    public SnapshotCreationException(String message) {
        super(message);
    }

    public SnapshotCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}