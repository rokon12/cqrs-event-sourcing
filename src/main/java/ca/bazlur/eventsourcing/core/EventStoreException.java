package ca.bazlur.eventsourcing.core;

public class EventStoreException extends RuntimeException {
    public EventStoreException(String message) {
        super(message);
    }

    public EventStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}