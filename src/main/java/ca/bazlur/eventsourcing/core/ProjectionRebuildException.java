package ca.bazlur.eventsourcing.core;

public class ProjectionRebuildException extends RuntimeException {
    public ProjectionRebuildException(String message) {
        super(message);
    }

    public ProjectionRebuildException(String message, Throwable cause) {
        super(message, cause);
    }
}