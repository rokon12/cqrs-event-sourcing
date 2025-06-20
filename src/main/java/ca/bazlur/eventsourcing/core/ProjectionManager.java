package ca.bazlur.eventsourcing.core;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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

    private static final int BATCH_SIZE = 500;

    public void rebuildAllProjections() {
        log.info("Rebuilding all projections...");

        projections.parallelStream().forEach(projection -> {
            try {
                rebuildProjection(projection);
            } catch (Exception e) {
                log.error("Failed to rebuild projection: {}", projection.getProjectionName(), e);
                throw new ProjectionRebuildException("Failed to rebuild projection: " + projection.getProjectionName(), e);
            }
        });

        log.info("All projections rebuilt successfully");
    }

    private void rebuildProjection(Projection<?> projection) {
        log.info("Rebuilding projection: {}", projection.getProjectionName());
        projection.reset();

        int offset = 0;
        long lastVersion = 0;
        int totalEvents = 0;

        while (true) {
            var events = eventStore.getAllEvents(offset, BATCH_SIZE);
            if (events.isEmpty()) {
                break;
            }

            events.forEach(projection::handle);
            lastVersion = events.getLast().getVersion();
            totalEvents += events.size();
            offset += events.size();

            log.debug("Processed batch of {} events for projection: {}", 
                events.size(), projection.getProjectionName());

            if (events.size() < BATCH_SIZE) {
                break;
            }
        }

        lastProcessedEventVersions.put(projection.getProjectionName(), lastVersion);
        log.info("Rebuilt projection: {} with {} events", 
            projection.getProjectionName(), totalEvents);
    }

    public CompletableFuture<Void> processNewEvents() {
        return CompletableFuture.runAsync(() -> {
            projections.parallelStream().forEach(this::processNewEventsForProjection);
        });
    }

    private void processNewEventsForProjection(Projection<?> projection) {
        try {
            var projectionName = projection.getProjectionName();
            var lastProcessedVersion = lastProcessedEventVersions.getOrDefault(projectionName, 0L);

            int offset = 0;
            long currentVersion = lastProcessedVersion;

            while (true) {
                var newEvents = eventStore.getAllEvents(offset, BATCH_SIZE);
                if (newEvents.isEmpty()) {
                    break;
                }

                var eventsToProcess = newEvents.stream()
                    .filter(event -> event.getVersion() > lastProcessedVersion)
                    .toList();

                if (eventsToProcess.isEmpty()) {
                    break;
                }

                log.debug("Processing batch of {} new events for projection: {}", 
                    eventsToProcess.size(), projectionName);

                eventsToProcess.forEach(projection::handle);
                currentVersion = eventsToProcess.getLast().getVersion();

                if (newEvents.size() < BATCH_SIZE) {
                    break;
                }

                offset += BATCH_SIZE;
            }

            if (currentVersion > lastProcessedVersion) {
                lastProcessedEventVersions.put(projectionName, currentVersion);
                log.info("Updated projection {} to version {}", projectionName, currentVersion);
            }
        } catch (Exception e) {
            log.error("Failed to process new events for projection: {}", projection.getProjectionName(), e);
            throw new ProjectionRebuildException("Failed to process new events for projection: " + 
                projection.getProjectionName(), e);
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
