package ca.bazlur.eventsourcing.core;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public abstract class AggregateRoot {
    @Getter
    protected String id;
    @Getter
    protected long version;
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    protected AggregateRoot(String id) {
        this.id = id;
        this.version = 0;
    }

    protected void applyEvent(DomainEvent event) {
        handleEvent(event);
        uncommittedEvents.add(event);
        version = event.getVersion();
    }

    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    public void loadFromHistory(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            handleEvent(event);
            version = event.getVersion();
        }
    }

    public List<DomainEvent> getUncommittedEvents() {
        return List.copyOf(uncommittedEvents);
    }

    protected abstract void handleEvent(DomainEvent event);
}