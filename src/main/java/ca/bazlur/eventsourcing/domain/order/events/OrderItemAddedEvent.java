package ca.bazlur.eventsourcing.domain.order.events;

import ca.bazlur.eventsourcing.core.DomainEvent;

import java.math.BigDecimal;

public class OrderItemAddedEvent extends DomainEvent {
    private final String productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal price;
    
    public OrderItemAddedEvent(String aggregateId, long version, String productId, String productName, 
                              int quantity, BigDecimal price, String correlationId, String causationId) {
        super(aggregateId, version, correlationId, causationId);
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
    }
    
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    
    @Override
    public String getEventType() {
        return "OrderItemAdded";
    }
}