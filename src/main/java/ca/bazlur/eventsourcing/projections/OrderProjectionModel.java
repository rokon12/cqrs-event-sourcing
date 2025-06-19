package ca.bazlur.eventsourcing.projections;

import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderProjectionModel {
    private String id;
    private String customerId;
    private OrderStatus status;
    private List<OrderItemProjection> items = new ArrayList<>();
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemProjection {
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal price;
        private BigDecimal totalPrice;
    }
}