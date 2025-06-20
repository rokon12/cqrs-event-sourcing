package ca.bazlur.eventsourcing.domain.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateOrderWithV1Event() {
        var orderId = UUID.randomUUID().toString();
        var customerId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        var order = Order.create(orderId, customerId, correlationId);

        assertEquals(customerId, order.getCustomerId());
        assertEquals(OrderStatus.DRAFT, order.getStatus());
        assertTrue(order.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());

        var events = order.getUncommittedEvents();
        assertEquals(1, events.size());
        var event = events.get(0);
        assertEquals("OrderCreated", event.getEventType());
        assertEquals(2, event.getSchemaVersion()); // Even V1 factory creates V2 events with defaults
    }

    @Test
    void shouldCreateOrderWithV2Event() {
        var orderId = UUID.randomUUID().toString();
        var customerId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();
        var customerType = "PREMIUM";
        var creditLimit = BigDecimal.valueOf(1000);

        var order = Order.createWithCustomerDetails(
            orderId,
            customerId,
            customerType,
            creditLimit,
            correlationId
        );

        assertEquals(customerId, order.getCustomerId());
        assertEquals(OrderStatus.DRAFT, order.getStatus());
        assertTrue(order.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());

        var events = order.getUncommittedEvents();
        assertEquals(1, events.size());
        var event = events.get(0);
        assertEquals("OrderCreated", event.getEventType());
        assertEquals(2, event.getSchemaVersion());
    }

    @Test
    void shouldThrowExceptionForMissingCustomerId() {
        var orderId = UUID.randomUUID().toString();
        
        assertThrows(IllegalArgumentException.class,
            () -> Order.create(orderId, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> Order.create(orderId, "", null));
    }

    @Test
    void shouldThrowExceptionForInvalidV2Parameters() {
        var orderId = UUID.randomUUID().toString();
        var customerId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        assertThrows(IllegalArgumentException.class,
            () -> Order.createWithCustomerDetails(
                orderId, customerId, null, BigDecimal.ONE, correlationId));

        assertThrows(IllegalArgumentException.class,
            () -> Order.createWithCustomerDetails(
                orderId, customerId, "PREMIUM", null, correlationId));
    }

    @Test
    void shouldAddItemToOrder() {
        var order = Order.create(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        );

        order.addItem(
            "PROD-1",
            "Test Product",
            2,
            BigDecimal.valueOf(10.00),
            UUID.randomUUID().toString()
        );

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(1, order.getItems().size());
        assertEquals(BigDecimal.valueOf(20.00), order.getTotalAmount());
    }

    @Test
    void shouldNotAddItemToConfirmedOrder() {
        var order = Order.create(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        );

        order.addItem(
            "PROD-1",
            "Test Product",
            1,
            BigDecimal.TEN,
            UUID.randomUUID().toString()
        );

        assertThrows(IllegalStateException.class, () ->
            order.addItem(
                "PROD-2",
                "Another Product",
                1,
                BigDecimal.TEN,
                UUID.randomUUID().toString()
            )
        );
    }
}