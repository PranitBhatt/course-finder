package com.coursefinder.dto.response;

import com.coursefinder.entity.CourseSource;
import com.coursefinder.entity.SyncStatus;

import java.time.Instant;

/**
 * Status of one course platform.
 *
 * @param syncMode         FULL_CATALOG (scheduled sync) or ON_DEMAND (fetched per topic)
 * @param enabled          false if switched off or missing config (e.g. no API key)
 * @param lastSyncedAt     most recent {@code last_synced_at} across this source's courses
 * @param lastSyncStatus   result of the latest catalog sync run, null for on-demand sources
 * @param lastSyncFinishedAt when the latest catalog sync run finished
 */
public record SourceDto(
        CourseSource source,
        String displayName,
        SyncMode syncMode,
        boolean enabled,
        long courseCount,
        Instant lastSyncedAt,
        SyncStatus lastSyncStatus,
        Instant lastSyncFinishedAt
) {

    public enum SyncMode {
        FULL_CATALOG,
        ON_DEMAND
    }
}
