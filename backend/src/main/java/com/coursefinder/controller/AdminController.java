package com.coursefinder.controller;

import com.coursefinder.dto.response.AdminStatsDto;
import com.coursefinder.dto.response.SyncResultDto;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.service.CatalogSyncService;
import com.coursefinder.service.StatsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints. Every request needs the {@code X-Admin-Token} header
 * (checked by {@link com.coursefinder.security.AdminTokenFilter} before reaching this class).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CatalogSyncService catalogSyncService;
    private final StatsService statsService;

    public AdminController(CatalogSyncService catalogSyncService, StatsService statsService) {
        this.catalogSyncService = catalogSyncService;
        this.statsService = statsService;
    }

    /**
     * {@code POST /api/admin/sync/COURSERA} - starts a background sync and returns 202 immediately.
     * Safe to call repeatedly: courses are upserted by (source, external_id).
     * Progress: {@code GET /api/sources}. Called nightly by .github/workflows/nightly-sync.yml.
     */
    @PostMapping("/sync/{source}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncResultDto triggerSync(@PathVariable CourseSource source) {
        catalogSyncService.startSync(source);
        return new SyncResultDto(source, "ACCEPTED",
                "%s sync started. Check GET /api/sources for progress.".formatted(source.getDisplayName()));
    }

    /** {@code GET /api/admin/stats} - search analytics from search_log. */
    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return statsService.getStats();
    }
}
