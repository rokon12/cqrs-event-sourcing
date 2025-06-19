package ca.bazlur.eventsourcing.projections;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.Projection;
import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class OrderProjection implements Projection<OrderProjectionModel> {
    private static final Logger log = LoggerFactory.getLogger(OrderProjection.class);
    
    private final ConcurrentMap<String, OrderProjectionModel> orders = new ConcurrentHashMap<>();
    
    @Override
    public void handle(DomainEvent event) {
        try {
            switch (event) {
                case OrderCreatedEvent e -> handle(e);
                case OrderItemAddedEvent e -> handle(e);
                default -> log.debug("Ignoring event type: {}", event.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Error handling event: {} for order: {}", 
                event.getClass().getSimpleName(), event.getAggregateId(), e);
        }
    }
    
    private void handle(OrderCreatedEvent event) {
        var order = new OrderProjectionModel();
        order.setId(event.getAggregateId());
        order.setCustomerId(event.getCustomerId());
        order.setStatus(OrderStatus.DRAFT);
        order.setCreatedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now());
        order.setUpdatedAt(order.getCreatedAt());
        order.setVersion(event.getVersion());
        
        orders.put(event.getAggregateId(), order);
        
        log.debug("Order projection created: {}", event.getAggregateId());
    }
    
    private void handle(OrderItemAddedEvent event) {
        var order = orders.get(event.getAggregateId());
        if (order == null) {
            log.warn("Order not found for OrderItemAddedEvent: {}", event.getAggregateId());
            return;
        }
        
        var totalPrice = event.getPrice().multiply(BigDecimal.valueOf(event.getQuantity()));
        var item = new OrderProjectionModel.OrderItemProjection(
            event.getProductId(),
            event.getProductName(),
            event.getQuantity(),
            event.getPrice(),
            totalPrice
        );
        
        order.getItems().add(item);
        order.setTotalAmount(order.getTotalAmount().add(totalPrice));
        order.setUpdatedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now());
        order.setVersion(event.getVersion());
        
        if (!order.getItems().isEmpty() && order.getStatus() == OrderStatus.DRAFT) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        
        log.debug("Order item added to projection: {} - {}", 
            event.getAggregateId(), event.getProductName());
    }
    
    @Override
    public OrderProjectionModel getById(String id) {
        return orders.get(id);
    }
    
    public List<OrderProjectionModel> getByCustomerId(String customerId) {
        return orders.values().stream()
            .filter(order -> customerId.equals(order.getCustomerId()))
            .toList();
    }
    
    public List<OrderProjectionModel> getAll() {
        return List.copyOf(orders.values());
    }
    
    @Override
    public void reset() {
        orders.clear();
        log.info("Order projection reset");
    }
    
    @Override
    public String getProjectionName() {
        return "OrderProjection";
    }
}