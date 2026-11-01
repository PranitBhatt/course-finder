package com.coursefinder.connector;

import com.coursefinder.entity.CourseSource;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Platform-neutral course data produced by a connector, ready to be upserted.
 *
 * @param free   null when the platform doesn't tell us
 * @param rating 0-5, null when unknown
 */
public record ConnectorCourse(
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
        BigDecimal rating
) {

    public ConnectorCourse {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(url, "url");
    }
}
