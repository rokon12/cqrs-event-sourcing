package ca.bazlur.eventsourcing.core;

import java.util.List;

public interface EventStore {

    void appendEvents(String streamId, List<DomainEvent> events, long expectedVersion);

    List<DomainEvent> getEvents(String streamId);

    List<DomainEvent> getEvents(String streamId, long fromVersion);

    List<DomainEvent> getAllEvents();

    List<DomainEvent> getAllEvents(long fromVersion);
}