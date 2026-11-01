package com.coursefinder.service;

import com.coursefinder.connector.CatalogConnector;
import com.coursefinder.connector.CatalogConnector.CatalogFetchResult;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.entity.SyncRun;
import com.coursefinder.exception.SyncInProgressException;
import com.coursefinder.exception.UnsupportedSyncException;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.repository.SyncRunRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs full catalog syncs: page through the platform, upsert each page, then remove
 * courses that disappeared from the catalog.
 * <p>
 * Only one sync per source runs at a time. A failed sync keeps whatever was already stored
 * and never deletes anything.
 */
@Service
public class CatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

    private final Map<CourseSource, CatalogConnector> connectors = new EnumMap<>(CourseSource.class);
    private final CourseRepository courseRepository;
    private final SyncRunRepository syncRunRepository;

    private final Set<CourseSource> runningSyncs = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CatalogSyncService(List<CatalogConnector> catalogConnectors,
                              CourseRepository courseRepository,
                              SyncRunRepository syncRunRepository) {
        catalogConnectors.forEach(connector -> connectors.put(connector.source(), connector));
        this.courseRepository = courseRepository;
        this.syncRunRepository = syncRunRepository;
    }

    public boolean supportsCatalogSync(CourseSource source) {
        return connectors.containsKey(source);
    }

    public boolean isRunning(CourseSource source) {
        return runningSyncs.contains(source);
    }

    /**
     * Starts a sync in the background and returns immediately.
     *
     * @throws UnsupportedSyncException if the source has no catalog connector or is disabled
     * @throws SyncInProgressException  if a sync for this source is already running
     */
    public void startSync(CourseSource source) {
        CatalogConnector connector = acquire(source);
        executor.execute(() -> runAndRelease(connector));
    }

    /**
     * Runs a sync on the calling thread (used by the scheduler).
     *
     * @return the finished run, successful or failed
     */
    public SyncRun syncNow(CourseSource source) {
        return runAndRelease(acquire(source));
    }

    private CatalogConnector acquire(CourseSource source) {
        CatalogConnector connector = connectors.get(source);
        if (connector == null) {
            throw new UnsupportedSyncException(
                    "%s is fetched on demand and has no full catalog sync".formatted(source.getDisplayName()));
        }
        if (!connector.isEnabled()) {
            throw new UnsupportedSyncException("%s connector is disabled".formatted(source.getDisplayName()));
        }
        if (!runningSyncs.add(source)) {
            throw new SyncInProgressException(source);
        }
        return connector;
    }

    private SyncRun runAndRelease(CatalogConnector connector) {
        try {
            return runSync(connector);
        } finally {
            runningSyncs.remove(connector.source());
        }
    }

    private SyncRun runSync(CatalogConnector connector) {
        CourseSource source = connector.source();
        SyncRun run = syncRunRepository.save(SyncRun.start(source));
        Instant startedAt = run.getStartedAt();
        AtomicInteger upserted = new AtomicInteger();
        log.info("{} sync started (run {})", source, run.getId());

        try {
            CatalogFetchResult result = connector.fetchCatalog(page ->
                    upserted.addAndGet(courseRepository.upsertAll(page, startedAt)));

            // Only a complete pass proves that unseen courses were really removed.
            int removed = result.complete()
                    ? courseRepository.deleteNotSyncedSince(source, startedAt)
                    : 0;

            run.markSuccess(upserted.get(), removed);
            log.info("{} sync finished: {} upserted, {} removed", source, upserted.get(), removed);
        } catch (RuntimeException e) {
            run.markFailed(upserted.get(), e.getMessage());
            log.error("{} sync failed after {} courses: {}", source, upserted.get(), e.getMessage(), e);
        }
        return syncRunRepository.save(run);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
