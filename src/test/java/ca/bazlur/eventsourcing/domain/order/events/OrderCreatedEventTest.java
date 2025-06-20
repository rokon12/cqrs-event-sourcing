package ca.bazlur.eventsourcing.domain.order.events;

import ca.bazlur.eventsourcing.core.EventSchemaException;
import ca.bazlur.eventsourcing.core.EventSchemaManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderCreatedEventTest {
    private EventSchemaManager schemaManager;

    @BeforeEach
    void setUp() {
        schemaManager = new EventSchemaManager();
        schemaManager.registerEventType(OrderCreatedEvent.class);
    }

    @Test
    void shouldCreateV2Event() {
        var orderId = UUID.randomUUID().toString();
        var customerId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        var event = new OrderCreatedEvent(
            orderId,
            1L,
            customerId,
            "PREMIUM",
            BigDecimal.valueOf(1000),
            correlationId,
            null
        );

        assertEquals(2, event.getSchemaVersion());
        assertEquals("PREMIUM", event.getCustomerType());
        assertEquals(BigDecimal.valueOf(1000), event.getCreditLimit());

        // Validate event schema
        assertDoesNotThrow(() -> schemaManager.validateEvent(event));
    }

    @Test
    void shouldCreateV1Event() {
        var orderId = UUID.randomUUID().toString();
        var customerId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        var event = new OrderCreatedEvent(
            orderId,
            1L,
            customerId,
            correlationId,
            null
        );

        assertEquals(2, event.getSchemaVersion());
        assertEquals("REGULAR", event.getCustomerType());
        assertEquals(BigDecimal.ZERO, event.getCreditLimit());
    }

    @Test
    void shouldEvolveV2ToV1() {
        var orderId = UUID.randomUUID().toString();
        var customerId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        var v2Event = new OrderCreatedEvent(
            orderId,
            1L,
            customerId,
            "PREMIUM",
            BigDecimal.valueOf(1000),
            correlationId,
            null
        );

        var v1Event = (OrderCreatedEvent) v2Event.evolve(1);

        assertEquals(2, v1Event.getSchemaVersion());
        assertEquals("REGULAR", v1Event.getCustomerType());
        assertEquals(BigDecimal.ZERO, v1Event.getCreditLimit());
        assertEquals(customerId, v1Event.getCustomerId());
        assertEquals(orderId, v1Event.getAggregateId());
    }

    @Test
    void shouldNotEvolveToUnsupportedVersion() {
        var orderId = UUID.randomUUID().toString();
        var customerId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        var event = new OrderCreatedEvent(
            orderId,
            1L,
            customerId,
            "PREMIUM",
            BigDecimal.valueOf(1000),
            correlationId,
            null
        );

        assertThrows(EventSchemaException.class, () -> event.evolve(3));
    }

    @Test
    void shouldCheckEvolutionSupport() {
        var orderId = UUID.randomUUID().toString();
        var customerId = UUID.randomUUID().toString();
        var correlationId = UUID.randomUUID().toString();

        var event = new OrderCreatedEvent(
            orderId,
            1L,
            customerId,
            "PREMIUM",
            BigDecimal.valueOf(1000),
            correlationId,
            null
        );

        assertTrue(event.canEvolve(1));
        assertTrue(event.canEvolve(2));
        assertFalse(event.canEvolve(3));
    }
}
