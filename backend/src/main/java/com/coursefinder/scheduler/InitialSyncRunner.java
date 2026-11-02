package com.coursefinder.scheduler;

import com.coursefinder.config.properties.CourseraProperties;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.exception.SyncInProgressException;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.service.CatalogSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, if there are no Coursera courses yet, starts a background sync so a fresh
 * install (or a fresh database) has data without anyone calling the admin endpoint.
 * Once courses exist, this does nothing.
 */
@Component
@ConditionalOnProperty(prefix = "app.coursera", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InitialSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialSyncRunner.class);

    private final CatalogSyncService catalogSyncService;
    private final CourseRepository courseRepository;
    private final CourseraProperties properties;

    public InitialSyncRunner(CatalogSyncService catalogSyncService,
                             CourseRepository courseRepository,
                             CourseraProperties properties) {
        this.catalogSyncService = catalogSyncService;
        this.courseRepository = courseRepository;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncIfEmpty() {
        if (!properties.syncOnStartupIfEmpty() || courseRepository.countBySource(CourseSource.COURSERA) > 0) {
            return;
        }
        log.info("No Coursera courses in the database yet - starting initial sync in the background");
        try {
            catalogSyncService.startSync(CourseSource.COURSERA);
        } catch (SyncInProgressException e) {
            log.info("Initial sync not started: {}", e.getMessage());
        }
    }
}
