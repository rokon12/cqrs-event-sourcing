package ca.bazlur.eventsourcing.core;

public class EventSchemaException extends RuntimeException {
    public EventSchemaException(String message) {
        super(message);
    }

    public EventSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}