package ca.bazlur.eventsourcing.projections;

import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderProjectionTest {

    private OrderProjection orderProjection;

    @BeforeEach
    void setUp() {
        orderProjection = new OrderProjection();
    }

    @Test
    void shouldCreateOrderProjectionFromOrderCreatedEvent() {
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-123";
        String correlationId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();

        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, 1L, customerId, correlationId, null
        );

        orderProjection.handle(event);

        OrderProjectionModel order = orderProjection.getById(orderId);
        assertNotNull(order);
        assertEquals(orderId, order.getId());
        assertEquals(customerId, order.getCustomerId());
        assertEquals(OrderStatus.DRAFT, order.getStatus());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertEquals(1L, order.getVersion());
        assertTrue(order.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());
    }

    @Test
    void shouldUpdateOrderProjectionWhenItemAdded() {
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-123";
        String correlationId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Instant updatedAt = createdAt.plusSeconds(10);

        OrderCreatedEvent createEvent = new OrderCreatedEvent(
                orderId, 1L, customerId, correlationId, null
        );
        orderProjection.handle(createEvent);

        OrderItemAddedEvent itemEvent = new OrderItemAddedEvent(
                orderId, 2L, "product-123", "Test Product", 
                2, BigDecimal.valueOf(50.00), correlationId, null
        );
        orderProjection.handle(itemEvent);

        OrderProjectionModel order = orderProjection.getById(orderId);
        assertNotNull(order);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(1, order.getItems().size());
        assertEquals(BigDecimal.valueOf(100.00), order.getTotalAmount());
        assertNotNull(order.getUpdatedAt());
        assertEquals(2L, order.getVersion());

        OrderProjectionModel.OrderItemProjection item = order.getItems().get(0);
        assertEquals("product-123", item.getProductId());
        assertEquals("Test Product", item.getProductName());
        assertEquals(2, item.getQuantity());
        assertEquals(BigDecimal.valueOf(50.00), item.getPrice());
        assertEquals(BigDecimal.valueOf(100.00), item.getTotalPrice());
    }

    @Test
    void shouldHandleMultipleItemsCorrectly() {
        String orderId = UUID.randomUUID().toString();
        String customerId = "customer-123";
        String correlationId = UUID.randomUUID().toString();

        OrderCreatedEvent createEvent = new OrderCreatedEvent(
                orderId, 1L, customerId, correlationId, null
        );
        orderProjection.handle(createEvent);

        OrderItemAddedEvent item1Event = new OrderItemAddedEvent(
                orderId, 2L, "product-1", "Product 1", 
                1, BigDecimal.valueOf(25.00), correlationId, null
        );
        orderProjection.handle(item1Event);

        OrderItemAddedEvent item2Event = new OrderItemAddedEvent(
                orderId, 3L, "product-2", "Product 2", 
                3, BigDecimal.valueOf(15.00), correlationId, null
        );
        orderProjection.handle(item2Event);

        OrderProjectionModel order = orderProjection.getById(orderId);
        assertNotNull(order);
        assertEquals(2, order.getItems().size());
        assertEquals(BigDecimal.valueOf(70.00), order.getTotalAmount()); // 25 + (3 * 15)
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void shouldIgnoreOrderItemEventForNonExistentOrder() {
        String orderId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        OrderItemAddedEvent itemEvent = new OrderItemAddedEvent(
                orderId, 1L, "product-1", "Product 1", 
                1, BigDecimal.valueOf(25.00), correlationId, null
        );

        assertDoesNotThrow(() -> orderProjection.handle(itemEvent));
        assertNull(orderProjection.getById(orderId));
    }

    @Test
    void shouldGetOrdersByCustomerId() {
        String customer1 = "customer-1";
        String customer2 = "customer-2";
        String correlationId = UUID.randomUUID().toString();

        // Create orders for customer 1
        orderProjection.handle(new OrderCreatedEvent(
                "order-1", 1L, customer1, correlationId, null
        ));
        orderProjection.handle(new OrderCreatedEvent(
                "order-2", 1L, customer1, correlationId, null
        ));

        // Create order for customer 2
        orderProjection.handle(new OrderCreatedEvent(
                "order-3", 1L, customer2, correlationId, null
        ));

        var customer1Orders = orderProjection.getByCustomerId(customer1);
        var customer2Orders = orderProjection.getByCustomerId(customer2);

        assertEquals(2, customer1Orders.size());
        assertEquals(1, customer2Orders.size());
        assertTrue(customer1Orders.stream().allMatch(o -> customer1.equals(o.getCustomerId())));
        assertTrue(customer2Orders.stream().allMatch(o -> customer2.equals(o.getCustomerId())));
    }

    @Test
    void shouldGetAllOrders() {
        String correlationId = UUID.randomUUID().toString();

        orderProjection.handle(new OrderCreatedEvent(
                "order-1", 1L, "customer-1", correlationId, null
        ));
        orderProjection.handle(new OrderCreatedEvent(
                "order-2", 1L, "customer-2", correlationId, null
        ));
        orderProjection.handle(new OrderCreatedEvent(
                "order-3", 1L, "customer-3", correlationId, null
        ));

        var allOrders = orderProjection.getAll();
        assertEquals(3, allOrders.size());
    }

    @Test
    void shouldResetProjection() {
        String orderId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        orderProjection.handle(new OrderCreatedEvent(
                orderId, 1L, "customer-1", correlationId, null
        ));

        assertNotNull(orderProjection.getById(orderId));

        orderProjection.reset();

        assertNull(orderProjection.getById(orderId));
        assertTrue(orderProjection.getAll().isEmpty());
    }

    @Test
    void shouldReturnCorrectProjectionName() {
        assertEquals("OrderProjection", orderProjection.getProjectionName());
    }

    @Test
    void shouldHandlePerformanceTestWithManyOrders() {
        String correlationId = UUID.randomUUID().toString();
        int numberOfOrders = 10_000;

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numberOfOrders; i++) {
            String orderId = "order-" + i;
            String customerId = "customer-" + (i % 100); // 100 different customers

            orderProjection.handle(new OrderCreatedEvent(
                    orderId, 1L, customerId, correlationId, null
            ));

            orderProjection.handle(new OrderItemAddedEvent(
                    orderId, 2L, "product-" + i, "Product " + i,
                    1, BigDecimal.valueOf(99.99), correlationId, null
            ));
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertEquals(numberOfOrders, orderProjection.getAll().size());
        assertTrue(duration < 5000, "Should process 10k orders in less than 5 seconds");

        // Test query performance
        startTime = System.currentTimeMillis();
        var customer0Orders = orderProjection.getByCustomerId("customer-0");
        endTime = System.currentTimeMillis();
        
        assertEquals(100, customer0Orders.size()); // 10k orders / 100 customers = 100 per customer
        assertTrue((endTime - startTime) < 100, "Query should complete in less than 100ms");
    }
}