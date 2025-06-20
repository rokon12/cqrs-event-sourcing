package ca.bazlur.eventsourcing.projections;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_item_projections")
@Getter
@Setter
public class OrderItemProjectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private OrderProjectionEntity order;

    public OrderProjectionModel.OrderItemProjection toModel() {
        return new OrderProjectionModel.OrderItemProjection(
            productId,
            productName,
            quantity,
            price,
            totalPrice
        );
    }

    public static OrderItemProjectionEntity fromModel(OrderProjectionModel.OrderItemProjection model) {
        var entity = new OrderItemProjectionEntity();
        entity.setProductId(model.getProductId());
        entity.setProductName(model.getProductName());
        entity.setQuantity(model.getQuantity());
        entity.setPrice(model.getPrice());
        entity.setTotalPrice(model.getTotalPrice());
        return entity;
    }
}