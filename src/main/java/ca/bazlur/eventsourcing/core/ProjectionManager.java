package ca.bazlur.eventsourcing.core;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class ProjectionManager {
    private static final Logger log = LoggerFactory.getLogger(ProjectionManager.class);
    
    private final EventStore eventStore;
    private final List<Projection<?>> projections;
    private final ConcurrentMap<String, Long> lastProcessedEventVersions = new ConcurrentHashMap<>();
    
    @Inject
    public ProjectionManager(EventStore eventStore, Instance<Projection<?>> projections) {
        this.eventStore = eventStore;
        this.projections = projections.stream().toList();
    }
    
    @PostConstruct
    public void initialize() {
        rebuildAllProjections();
    }
    
    public void rebuildAllProjections() {
        log.info("Rebuilding all projections...");
        
        projections.parallelStream().forEach(projection -> {
            log.info("Rebuilding projection: {}", projection.getProjectionName());
            projection.reset();
            
            var events = eventStore.getAllEvents();
            events.forEach(projection::handle);
            
            lastProcessedEventVersions.put(
                projection.getProjectionName(),
                    events.isEmpty() ? 0L : events.getLast().getVersion()
            );
            
            log.info("Rebuilt projection: {} with {} events",
                    Optional.ofNullable(projection.getProjectionName()), events.size());
        });
        
        log.info("All projections rebuilt successfully");
    }
    
    public CompletableFuture<Void> processNewEvents() {
        return CompletableFuture.runAsync(() -> {
            projections.parallelStream().forEach(this::processNewEventsForProjection);
        });
    }
    
    private void processNewEventsForProjection(Projection<?> projection) {
        var projectionName = projection.getProjectionName();
        var lastProcessedVersion = lastProcessedEventVersions.getOrDefault(projectionName, 0L);
        
        var newEvents = eventStore.getAllEvents(lastProcessedVersion + 1);
        
        if (!newEvents.isEmpty()) {
            log.debug("Processing {} new events for projection: {}", 
                newEvents.size(), projectionName);
            
            newEvents.forEach(projection::handle);
            
            lastProcessedEventVersions.put(
                projectionName, 
                newEvents.getLast().getVersion()
            );
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> Projection<T> getProjection(Class<? extends Projection<T>> projectionClass) {
        return (Projection<T>) projections.stream()
            .filter(p -> projectionClass.isAssignableFrom(p.getClass()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Projection not found: " + projectionClass.getSimpleName()));
    }
}