package ca.bazlur.eventsourcing.domain.order;

import ca.bazlur.eventsourcing.core.AggregateRoot;
import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Order extends AggregateRoot {
    private String customerId;
    private OrderStatus status;
    private List<OrderItem> items;
    private BigDecimal totalAmount;

    public Order(String id) {
        super(id);
        this.items = new ArrayList<>();
        this.totalAmount = BigDecimal.ZERO;
    }

    public static Order create(String orderId, String customerId, String correlationId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        Order order = new Order(orderId);
        order.applyEvent(new OrderCreatedEvent(
            orderId,
            order.version + 1,
            customerId,
            correlationId,
            null
        ));
        return order;
    }

    public static Order createWithCustomerDetails(
        String orderId,
        String customerId,
        String customerType,
        BigDecimal creditLimit,
        String correlationId
    ) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (customerType == null || customerType.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer type is required");
        }
        if (creditLimit == null) {
            throw new IllegalArgumentException("Credit limit is required");
        }

        Order order = new Order(orderId);
        order.applyEvent(new OrderCreatedEvent(
            orderId,
            order.version + 1,
            customerId,
            customerType,
            creditLimit,
            correlationId,
            null
        ));
        return order;
    }

    public void addItem(String productId, String productName, int quantity, BigDecimal price, String correlationId) {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Cannot add items to order in status: " + status);
        }

        applyEvent(new OrderItemAddedEvent(
                id,
                version + 1,
                productId,
                productName,
                quantity,
                price,
                correlationId,
                null
        ));
    }

    @Override
    protected void handleEvent(DomainEvent event) {
        switch (event) {
            case OrderCreatedEvent e -> handle(e);
            case OrderItemAddedEvent e -> handle(e);
            default -> throw new IllegalArgumentException("Unknown event type: " + event.getClass());
        }
    }

    private void handle(OrderCreatedEvent event) {
        this.customerId = event.getCustomerId();
        this.status = OrderStatus.DRAFT;
    }

    private void handle(OrderItemAddedEvent event) {
        OrderItem item = new OrderItem(
                event.getProductId(),
                event.getProductName(),
                event.getQuantity(),
                event.getPrice()
        );
        items.add(item);
        totalAmount = totalAmount.add(event.getPrice().multiply(BigDecimal.valueOf(event.getQuantity())));

        if (!items.isEmpty()) {
            status = OrderStatus.CONFIRMED;
        }
    }
}
