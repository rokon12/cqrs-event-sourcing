package ca.bazlur.eventsourcing.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EventStore {
    CompletableFuture<Void> appendEvents(String streamId, List<DomainEvent> events, long expectedVersion);
    
    CompletableFuture<List<DomainEvent>> getEvents(String streamId);
    
    CompletableFuture<List<DomainEvent>> getEvents(String streamId, long fromVersion);
    
    CompletableFuture<List<DomainEvent>> getAllEvents();
    
    CompletableFuture<List<DomainEvent>> getAllEvents(long fromVersion);
}