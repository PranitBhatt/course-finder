package com.coursefinder.repository.projection;

import com.coursefinder.entity.CourseSource;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of a ranked search result, read straight from SQL (no entity loading). */
public record CourseSearchRow(
        long id,
        CourseSource source,
        String externalId,
        String title,
        String description,
        String url,
        String thumbnailUrl,
        String provider,
        String language,
        String level,
        String durationText,
        Boolean free,
        BigDecimal rating,
        Instant lastSyncedAt,
        double rank
) {
}
