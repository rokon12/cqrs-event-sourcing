package ca.bazlur.eventsourcing.api.dto;


import ca.bazlur.eventsourcing.domain.order.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        String orderId,
        String customerId,
        OrderStatus status,
        Instant createdAt
) {
}
