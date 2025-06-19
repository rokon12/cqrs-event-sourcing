package ca.bazlur.eventsourcing.domain.order.events;

import ca.bazlur.eventsourcing.core.DomainEvent;

public class OrderCreatedEvent extends DomainEvent {
    private final String customerId;
    
    public OrderCreatedEvent(String aggregateId, long version, String customerId, String correlationId, String causationId) {
        super(aggregateId, version, correlationId, causationId);
        this.customerId = customerId;
    }
    
    public String getCustomerId() { return customerId; }
    
    @Override
    public String getEventType() {
        return "OrderCreated";
    }
}