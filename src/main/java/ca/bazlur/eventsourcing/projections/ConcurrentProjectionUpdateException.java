package ca.bazlur.eventsourcing.projections;

/**
 * Exception thrown when a concurrent modification is detected during projection update.
 * This typically happens when the optimistic locking detects a version mismatch.
 */
public class ConcurrentProjectionUpdateException extends RuntimeException {
    public ConcurrentProjectionUpdateException(String message) {
        super(message);
    }

    public ConcurrentProjectionUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}