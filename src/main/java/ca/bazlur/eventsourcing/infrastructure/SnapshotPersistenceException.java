package ca.bazlur.eventsourcing.infrastructure;

/**
 * Exception thrown when there are issues with snapshot persistence operations.
 */
public class SnapshotPersistenceException extends RuntimeException {
    public SnapshotPersistenceException(String message) {
        super(message);
    }

    public SnapshotPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}