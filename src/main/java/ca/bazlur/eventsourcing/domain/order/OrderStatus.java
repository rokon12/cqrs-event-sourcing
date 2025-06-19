package ca.bazlur.eventsourcing.domain.order;

public enum OrderStatus {
    DRAFT,
    CONFIRMED,
    SHIPPED,
    CANCELLED
}