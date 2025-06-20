package ca.bazlur.eventsourcing.projections;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.Projection;
import ca.bazlur.eventsourcing.domain.order.OrderStatus;
import ca.bazlur.eventsourcing.domain.order.events.OrderCreatedEvent;
import ca.bazlur.eventsourcing.domain.order.events.OrderItemAddedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApplicationScoped
public class OrderProjection implements Projection<OrderProjectionModel> {
    private static final Logger log = LoggerFactory.getLogger(OrderProjection.class);
    private static final int CACHE_SIZE = 1000;

    private final OrderProjectionRepository repository;
    private final Map<String, OrderProjectionModel> cache;
    private final Map<String, List<OrderProjectionModel>> customerCache;

    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    @Inject
    public OrderProjection(OrderProjectionRepository repository) {
        this.repository = repository;
        this.cache = new ConcurrentHashMap<>(CACHE_SIZE);
        this.customerCache = new ConcurrentHashMap<>();
    }

    private void updateCache(OrderProjectionModel model) {
        var writeLock = cacheLock.writeLock();
        try {
            writeLock.lock();
            cache.put(model.getId(), model);

            var customerOrders = customerCache.computeIfAbsent(
                model.getCustomerId(), 
                k -> new ArrayList<>()
            );
            customerOrders.removeIf(order -> order.getId().equals(model.getId()));
            customerOrders.add(model);

            evictOldestEntriesIfNeeded();
        } finally {
            writeLock.unlock();
        }

        log.debug("Cache updated for order: {}, cache size: {}", model.getId(), cache.size());
    }

    private void invalidateCache() {
        var writeLock = cacheLock.writeLock();
        try {
            writeLock.lock();
            cache.clear();
            customerCache.clear();
            log.debug("Cache invalidated");
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void handle(DomainEvent event) {
        try {
            switch (event) {
                case OrderCreatedEvent e -> handle(e);
                case OrderItemAddedEvent e -> handle(e);
                default -> log.debug("Ignoring event type: {}", event.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Error handling event: {} for order: {}", 
                event.getClass().getSimpleName(), event.getAggregateId(), e);
        }
    }

    @Transactional
    private void handle(OrderCreatedEvent event) {
        var model = new OrderProjectionModel();
        model.setId(event.getAggregateId());
        model.setCustomerId(event.getCustomerId());
        model.setStatus(OrderStatus.DRAFT);
        model.setCreatedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now());
        model.setUpdatedAt(model.getCreatedAt());
        model.setVersion(event.getVersion());

        var entity = OrderProjectionEntity.fromModel(model);
        repository.save(entity);
        updateCache(model);

        log.debug("Order projection created: {}", event.getAggregateId());
    }

    @Transactional
    private void handle(OrderItemAddedEvent event) {
        var model = getById(event.getAggregateId());
        if (model == null) {
            log.warn("Order not found for OrderItemAddedEvent: {}", event.getAggregateId());
            return;
        }

        var totalPrice = event.getPrice().multiply(BigDecimal.valueOf(event.getQuantity()));
        var item = new OrderProjectionModel.OrderItemProjection(
            event.getProductId(),
            event.getProductName(),
            event.getQuantity(),
            event.getPrice(),
            totalPrice
        );

        model.getItems().add(item);
        model.setTotalAmount(model.getTotalAmount().add(totalPrice));
        model.setUpdatedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now());
        model.setVersion(event.getVersion());

        if (!model.getItems().isEmpty() && model.getStatus() == OrderStatus.DRAFT) {
            model.setStatus(OrderStatus.CONFIRMED);
        }

        repository.save(OrderProjectionEntity.fromModel(model));
        updateCache(model);

        log.debug("Order item added to projection: {} - {}", 
            event.getAggregateId(), event.getProductName());
    }

    @Override
    public OrderProjectionModel getById(String id) {
        var readLock = cacheLock.readLock();
        try {
            readLock.lock();
            var cached = cache.get(id);
            if (cached != null) {
                log.debug("Cache hit for order: {}", id);
                return cached;
            }
        } finally {
            readLock.unlock();
        }

        var model = repository.findById(id)
            .map(OrderProjectionEntity::toModel)
            .orElse(null);

        if (model != null) {
            updateCache(model);
            log.debug("Cache updated for order: {}", id);
        }

        return model;
    }

    public List<OrderProjectionModel> getByCustomerId(String customerId) {
        var readLock = cacheLock.readLock();
        try {
            readLock.lock();
            var cached = customerCache.get(customerId);
            if (cached != null) {
                log.debug("Cache hit for customer orders: {}", customerId);
                return List.copyOf(cached);
            }
        } finally {
            readLock.unlock();
        }

        var models = repository.findByCustomerId(customerId).stream()
            .map(OrderProjectionEntity::toModel)
            .toList();

        if (!models.isEmpty()) {
            var writeLock = cacheLock.writeLock();
            try {
                writeLock.lock();
                customerCache.put(customerId, new ArrayList<>(models));
                models.forEach(model -> cache.put(model.getId(), model));
                log.debug("Cache updated for customer orders: {}", customerId);
            } finally {
                writeLock.unlock();
            }
        }

        return models;
    }

    public List<OrderProjectionModel> getAll() {
        var readLock = cacheLock.readLock();
        try {
            readLock.lock();
            if (!cache.isEmpty()) {
                log.debug("Returning all orders from cache, size: {}", cache.size());
                return List.copyOf(cache.values());
            }
        } finally {
            readLock.unlock();
        }

        var models = repository.findAll().stream()
            .map(OrderProjectionEntity::toModel)
            .toList();

        if (!models.isEmpty()) {
            var writeLock = cacheLock.writeLock();
            try {
                writeLock.lock();
                cache.clear();
                customerCache.clear();
                models.forEach(model -> {
                    cache.put(model.getId(), model);
                    customerCache.computeIfAbsent(model.getCustomerId(), k -> new ArrayList<>())
                        .add(model);
                });
                log.debug("Cache populated with {} orders", models.size());
            } finally {
                writeLock.unlock();
            }
        }

        return models;
    }

    private void evictOldestEntriesIfNeeded() {
        if (cache.size() > CACHE_SIZE) {
            var writeLock = cacheLock.writeLock();
            try {
                writeLock.lock();
                var entriesToRemove = cache.size() - CACHE_SIZE;
                var oldestEntries = cache.values().stream()
                    .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                    .limit(entriesToRemove)
                    .toList();

                oldestEntries.forEach(model -> {
                    cache.remove(model.getId());
                    var customerOrders = customerCache.get(model.getCustomerId());
                    if (customerOrders != null) {
                        customerOrders.removeIf(order -> order.getId().equals(model.getId()));
                        if (customerOrders.isEmpty()) {
                            customerCache.remove(model.getCustomerId());
                        }
                    }
                });
                log.debug("Evicted {} old entries from cache", entriesToRemove);
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public void reset() {
        repository.deleteAll();
        invalidateCache();
        log.info("Order projection reset");
    }

    @Override
    public String getProjectionName() {
        return "OrderProjection";
    }
}
