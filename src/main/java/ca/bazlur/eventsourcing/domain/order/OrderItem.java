package ca.bazlur.eventsourcing.domain.order;

import java.math.BigDecimal;

public record OrderItem(String productId, String productName, int quantity, BigDecimal price) {
}