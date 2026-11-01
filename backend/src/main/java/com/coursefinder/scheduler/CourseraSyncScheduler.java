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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly full Coursera catalog sync, plus a one-off sync at startup when the table is empty
 * so a fresh setup has data straight away.
 */
@Component
@ConditionalOnProperty(prefix = "app.coursera", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CourseraSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CourseraSyncScheduler.class);

    private final CatalogSyncService catalogSyncService;
    private final CourseRepository courseRepository;
    private final CourseraProperties properties;

    public CourseraSyncScheduler(CatalogSyncService catalogSyncService,
                                 CourseRepository courseRepository,
                                 CourseraProperties properties) {
        this.catalogSyncService = catalogSyncService;
        this.courseRepository = courseRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.coursera.sync-cron:0 0 3 * * *}")
    public void nightlySync() {
        try {
            catalogSyncService.syncNow(CourseSource.COURSERA);
        } catch (SyncInProgressException e) {
            log.info("Skipping scheduled Coursera sync: {}", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartupIfEmpty() {
        if (!properties.syncOnStartupIfEmpty()) {
            return;
        }
        if (courseRepository.countBySource(CourseSource.COURSERA) == 0) {
            log.info("No Coursera courses in the database yet - starting initial sync in the background");
            catalogSyncService.startSync(CourseSource.COURSERA);
        }
    }
}
