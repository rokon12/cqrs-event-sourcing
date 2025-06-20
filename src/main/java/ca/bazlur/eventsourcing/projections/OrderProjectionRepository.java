package ca.bazlur.eventsourcing.projections;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrderProjectionRepository {
    private static final Logger log = LoggerFactory.getLogger(OrderProjectionRepository.class);
    
    private final EntityManager entityManager;
    
    @Inject
    public OrderProjectionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
    @Transactional
    public void save(OrderProjectionEntity order) {
        if (order.getId() == null) {
            entityManager.persist(order);
            log.debug("Created new order projection: {}", order.getId());
        } else {
            entityManager.merge(order);
            log.debug("Updated order projection: {}", order.getId());
        }
    }
    
    public Optional<OrderProjectionEntity> findById(String id) {
        return Optional.ofNullable(entityManager.find(OrderProjectionEntity.class, id));
    }
    
    public List<OrderProjectionEntity> findByCustomerId(String customerId) {
        TypedQuery<OrderProjectionEntity> query = entityManager.createQuery(
            "SELECT o FROM OrderProjectionEntity o WHERE o.customerId = :customerId",
            OrderProjectionEntity.class
        );
        query.setParameter("customerId", customerId);
        return query.getResultList();
    }
    
    public List<OrderProjectionEntity> findAll() {
        TypedQuery<OrderProjectionEntity> query = entityManager.createQuery(
            "SELECT o FROM OrderProjectionEntity o",
            OrderProjectionEntity.class
        );
        return query.getResultList();
    }
    
    @Transactional
    public void deleteAll() {
        entityManager.createQuery("DELETE FROM OrderProjectionEntity").executeUpdate();
        entityManager.createQuery("DELETE FROM OrderItemProjectionEntity").executeUpdate();
        log.info("Deleted all order projections");
    }
}