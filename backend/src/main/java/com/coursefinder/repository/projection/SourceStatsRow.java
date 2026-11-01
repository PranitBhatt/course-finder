package com.coursefinder.repository.projection;

import com.coursefinder.entity.CourseSource;

import java.time.Instant;

/** Course count and most recent sync time for one source. */
public record SourceStatsRow(
        CourseSource source,
        Long courseCount,
        Instant lastSyncedAt
) {
}
