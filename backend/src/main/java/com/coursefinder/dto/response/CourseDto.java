package com.coursefinder.dto.response;

import com.coursefinder.entity.CourseSource;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A course as returned by the API.
 *
 * @param free null when the platform doesn't say (e.g. Coursera)
 */
public record CourseDto(
        long id,
        CourseSource source,
        String sourceName,
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
        Instant lastSyncedAt
) {
}
