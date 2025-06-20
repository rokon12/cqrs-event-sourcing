package ca.bazlur.eventsourcing.projections;

/**
 * Exception thrown when there is a general persistence error while saving or updating projections.
 * This could be due to database connectivity issues, constraint violations, or other persistence-related problems.
 */
public class ProjectionPersistenceException extends RuntimeException {
    public ProjectionPersistenceException(String message) {
        super(message);
    }

    public ProjectionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}