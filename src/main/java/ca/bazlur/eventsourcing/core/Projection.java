package ca.bazlur.eventsourcing.core;

public interface Projection<T> {
    void handle(DomainEvent event);
    T getById(String id);
    void reset();
    String getProjectionName();
}