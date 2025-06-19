package ca.bazlur.eventsourcing.infrastructure;

import ca.bazlur.eventsourcing.core.DomainEvent;
import ca.bazlur.eventsourcing.core.Projection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class ProjectionUpdater {
    private static final Logger log = LoggerFactory.getLogger(ProjectionUpdater.class);
    
    private final List<Projection<?>> projections;
    
    @Inject
    public ProjectionUpdater(Instance<Projection<?>> projections) {
        this.projections = projections.stream().toList();
    }
    
    public CompletableFuture<Void> updateProjections(List<DomainEvent> events) {
        return CompletableFuture.runAsync(() -> {
            log.debug("Updating projections with {} events", events.size());
            
            projections.parallelStream().forEach(projection -> {
                try {
                    events.forEach(projection::handle);
                    log.debug("Updated projection: {} with {} events", 
                        projection.getProjectionName(), events.size());
                } catch (Exception e) {
                    log.error("Error updating projection: {}", 
                        projection.getProjectionName(), e);
                }
            });
        });
    }
    
    public void updateProjectionsSync(List<DomainEvent> events) {
        log.debug("Synchronously updating projections with {} events", events.size());
        
        for (var projection : projections) {
            try {
                events.forEach(projection::handle);
                log.debug("Updated projection: {} with {} events", 
                    projection.getProjectionName(), events.size());
            } catch (Exception e) {
                log.error("Error updating projection: {}", 
                    projection.getProjectionName(), e);
            }
        }
    }
}