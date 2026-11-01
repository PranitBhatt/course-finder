package com.coursefinder.repository;

import com.coursefinder.connector.ConnectorCourse;

import java.time.Instant;
import java.util.List;

/**
 * Bulk insert-or-update keyed on (source, external_id).
 */
public interface CourseUpsertRepository {

    /**
     * @param syncedAt written to {@code last_synced_at} on every row, so stale rows can be found later
     * @return number of rows processed
     */
    int upsertAll(List<ConnectorCourse> courses, Instant syncedAt);
}
