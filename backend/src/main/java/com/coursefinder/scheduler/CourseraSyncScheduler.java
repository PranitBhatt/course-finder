package com.coursefinder.scheduler;

import com.coursefinder.entity.CourseSource;
import com.coursefinder.exception.SyncInProgressException;
import com.coursefinder.service.CatalogSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly full Coursera catalog sync, for setups where the app runs continuously (local, VPS).
 * <p>
 * Off in prod ({@code app.coursera.scheduled-sync=false}): free Render instances sleep and restart,
 * so a GitHub Actions workflow calls {@code POST /api/admin/sync/COURSERA} instead.
 */
@Component
@ConditionalOnExpression("${app.coursera.enabled:true} and ${app.coursera.scheduled-sync:true}")
public class CourseraSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CourseraSyncScheduler.class);

    private final CatalogSyncService catalogSyncService;

    public CourseraSyncScheduler(CatalogSyncService catalogSyncService) {
        this.catalogSyncService = catalogSyncService;
    }

    @Scheduled(cron = "${app.coursera.sync-cron:0 0 3 * * *}")
    public void nightlySync() {
        try {
            catalogSyncService.syncNow(CourseSource.COURSERA);
        } catch (SyncInProgressException e) {
            log.info("Skipping scheduled Coursera sync: {}", e.getMessage());
        }
    }
}
