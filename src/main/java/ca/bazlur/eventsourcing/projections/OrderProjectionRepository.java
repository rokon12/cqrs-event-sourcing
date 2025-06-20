package ca.bazlur.eventsourcing.projections;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Repository for managing order projections using Panache.
 * Provides CRUD operations and custom queries with pagination support.
 */
@ApplicationScoped
public class OrderProjectionRepository implements PanacheRepository<OrderProjectionEntity> {
    private static final Logger log = LoggerFactory.getLogger(OrderProjectionRepository.class);

    @Transactional
    public void save(OrderProjectionEntity order) {
        if (order == null) {
            throw new IllegalArgumentException("Order must not be null");
        }
        try {
            if (order.getId() == null) {
                persist(order);
                log.debug("Created new order projection: {}", order.getId());
            } else {
                var existingOrder = find("id", order.getId()).firstResultOptional();
                if (existingOrder.isPresent()) {
                    var entity = existingOrder.get();
                    order.setOptimisticLockVersion(entity.getOptimisticLockVersion());
                    getEntityManager().detach(entity);
                }
                var merged = getEntityManager().merge(order);
                getEntityManager().flush();
                log.debug("Updated order projection: {} at version {}", 
                    merged.getId(), merged.getVersion());
            }
        } catch (PersistenceException e) {
            log.error("Failed to save order projection: {}", order.getId(), e);
            throw new ProjectionPersistenceException(
                "Failed to save order projection: " + order.getId(), e);
        }
    }

    /**
     * Finds an order projection by ID and throws an exception if not found.
     *
     * @param id the ID of the order projection
     * @return the order projection
     * @throws IllegalArgumentException if id is null or blank
     * @throws ProjectionPersistenceException if the projection is not found or there's a database error
     */
    public OrderProjectionEntity findByIdOrThrow(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID must not be null or blank");
        }
        try {
            return find("id", id).firstResultOptional()
                .orElseThrow(() -> new ProjectionPersistenceException("Order projection not found: " + id));
        } catch (PersistenceException e) {
            log.error("Failed to find order projection: {}", id, e);
            throw new ProjectionPersistenceException("Failed to find order projection: " + id, e);
        }
    }

    /**
     * Saves multiple order projections in a batch.
     *
     * @param orders the list of order projections to save
     * @throws IllegalArgumentException if orders is null
     * @throws ProjectionPersistenceException if there's a database error
     */
    @Transactional
    public void saveAll(List<OrderProjectionEntity> orders) {
        if (orders == null) {
            throw new IllegalArgumentException("Orders list must not be null");
        }
        try {
            for (OrderProjectionEntity order : orders) {
                persist(order);
            }
            log.debug("Saved {} order projections", orders.size());
        } catch (PersistenceException e) {
            log.error("Failed to save {} order projections", orders.size(), e);
            throw new ProjectionPersistenceException("Failed to save order projections in batch", e);
        }
    }

    public boolean exists(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID must not be null or blank");
        }
        return count("id = ?1", id) > 0;
    }

    public List<OrderProjectionEntity> listAll() {
        try {
            return findAll(Sort.by("createdAt").descending()).list();
        } catch (PersistenceException e) {
            log.error("Failed to find all order projections", e);
            throw new ProjectionPersistenceException("Failed to find all order projections", e);
        }
    }

    public java.util.Optional<OrderProjectionEntity> findById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID must not be null or blank");
        }
        try {
            return find("id", id).firstResultOptional();
        } catch (PersistenceException e) {
            log.error("Failed to find order projection by id: {}", id, e);
            throw new ProjectionPersistenceException("Failed to find order projection: " + id, e);
        }
    }

    public Page<OrderProjectionEntity> findByCustomerId(String customerId, PageRequest pageRequest) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID must not be null or blank");
        }
        if (pageRequest == null) {
            throw new IllegalArgumentException("PageRequest must not be null");
        }

        try {
            var count = count("customerId = ?1", customerId);
            var results = find("customerId = ?1", Sort.by("createdAt").descending(), customerId)
                .page(pageRequest.page(), pageRequest.size())
                .list();

            log.debug("Found {} order projections for customer: {} page: {}", 
                results.size(), customerId, pageRequest.page());

            return Page.of(results, pageRequest, count);
        } catch (PersistenceException e) {
            log.error("Failed to find order projections for customer: {}", customerId, e);
            throw new ProjectionPersistenceException(
                "Failed to find order projections for customer: " + customerId, e);
        }
    }

    public Page<OrderProjectionEntity> findAllPaged(PageRequest pageRequest) {
        if (pageRequest == null) {
            throw new IllegalArgumentException("PageRequest must not be null");
        }

        try {
            var count = count();
            var results = findAll(Sort.by("createdAt").descending())
                .page(pageRequest.page(), pageRequest.size())
                .list();

            log.debug("Found {} order projections for page {}", 
                results.size(), pageRequest.page());

            return Page.of(results, pageRequest, count);
        } catch (PersistenceException e) {
            log.error("Failed to find all order projections", e);
            throw new ProjectionPersistenceException("Failed to find all order projections", e);
        }
    }

    @Transactional
    public void deleteAllWithItems() {
        try {
            log.info("Starting deletion of all order projections and items...");

            // Delete items first to maintain referential integrity
            var itemsDeleted = getEntityManager()
                .createQuery("DELETE FROM OrderItemProjectionEntity")
                .executeUpdate();
            log.debug("Deleted {} order items", itemsDeleted);

            // Then delete orders using Panache's deleteAll
            var ordersDeleted = (int) deleteAll();
            log.debug("Deleted {} orders", ordersDeleted);

            log.info("Successfully completed deletion of {} orders and {} items", 
                ordersDeleted, itemsDeleted);
        } catch (PersistenceException e) {
            log.error("Failed to delete all projections", e);
            throw new ProjectionPersistenceException("Failed to delete all projections", e);
        }
    }
}
