package ca.bazlur.eventsourcing.domain.order.events;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.EventSchemaVersion;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@EventSchemaVersion(value = 2, description = "Added customer type and initial credit limit")
public class OrderCreatedEvent extends DomainEvent {
    private final String customerId;
    private final String customerType;
    private final BigDecimal creditLimit;
    private final Instant orderDate;

    /**
     * V2 Constructor with full customer details
     */
    public OrderCreatedEvent(
        String orderId,
        long version,
        String customerId,
        String customerType,
        BigDecimal creditLimit,
        String correlationId,
        String causationId
    ) {
        super(orderId, version, correlationId, causationId);
        this.customerId = customerId;
        this.customerType = customerType;
        this.creditLimit = creditLimit;
        this.orderDate = Instant.now();
    }

    /**
     * V1 Constructor for backward compatibility
     * Creates an event with default V1 values (REGULAR customer type and zero credit limit)
     */
    public OrderCreatedEvent(
        String orderId,
        long version,
        String customerId,
        String correlationId,
        String causationId
    ) {
        this(orderId, version, customerId, "REGULAR", BigDecimal.ZERO, correlationId, causationId);
    }


    @Override
    public DomainEvent evolve(int targetVersion) {
        if (targetVersion == 1 && this.getSchemaVersion() == 2) {
            // Downgrade from V2 to V1 by using the V1 constructor
            return new OrderCreatedEvent(
                getAggregateId(),
                getVersion(),
                getCustomerId(),
                getCorrelationId(),
                getCausationId()
            );
        }
        return super.evolve(targetVersion);
    }
}
