package com.coursefinder.controller;

import com.coursefinder.dto.response.SyncResultDto;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.service.CatalogSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only endpoint to trigger a sync manually. Has no authentication, so it only exists
 * when {@code app.admin.enabled=true} (set in the {@code dev} profile); otherwise it returns 404.
 */
@RestController
@RequestMapping("/api/admin")
@ConditionalOnProperty(prefix = "app.admin", name = "enabled", havingValue = "true")
public class AdminSyncController {

    private final CatalogSyncService catalogSyncService;

    public AdminSyncController(CatalogSyncService catalogSyncService) {
        this.catalogSyncService = catalogSyncService;
    }

    /**
     * {@code POST /api/admin/sync/coursera} - starts a background sync and returns 202.
     * Check progress with {@code GET /api/sources}.
     */
    @PostMapping("/sync/{source}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncResultDto triggerSync(@PathVariable CourseSource source) {
        catalogSyncService.startSync(source);
        return new SyncResultDto(source, "ACCEPTED",
                "%s sync started. Check GET /api/sources for progress.".formatted(source.getDisplayName()));
    }
}
