package ca.bazlur.eventsourcing.projections;

import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_projections")
@Getter
@Setter
public class OrderProjectionEntity {
    @Id
    private String id;
    
    private String customerId;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItemProjectionEntity> items = new ArrayList<>();
    
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;
    
    @Version
    private Long optimisticLockVersion;
    
    public OrderProjectionModel toModel() {
        var model = new OrderProjectionModel();
        model.setId(id);
        model.setCustomerId(customerId);
        model.setStatus(status);
        model.setTotalAmount(totalAmount);
        model.setCreatedAt(createdAt);
        model.setUpdatedAt(updatedAt);
        model.setVersion(version);
        model.setItems(items.stream().map(OrderItemProjectionEntity::toModel).toList());
        return model;
    }
    
    public static OrderProjectionEntity fromModel(OrderProjectionModel model) {
        var entity = new OrderProjectionEntity();
        entity.setId(model.getId());
        entity.setCustomerId(model.getCustomerId());
        entity.setStatus(model.getStatus());
        entity.setTotalAmount(model.getTotalAmount());
        entity.setCreatedAt(model.getCreatedAt());
        entity.setUpdatedAt(model.getUpdatedAt());
        entity.setVersion(model.getVersion());
        entity.setItems(model.getItems().stream().map(OrderItemProjectionEntity::fromModel).toList());
        return entity;
    }
}