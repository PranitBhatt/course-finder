package com.coursefinder.dto.response;

import com.coursefinder.entity.CourseSource;
import com.coursefinder.entity.ProgressStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in "My Learning".
 *
 * @param entryId         public id of this entry; used to remove it even when the course is gone
 * @param courseId        null when the course was removed from our catalog by a sync
 * @param courseTitle     snapshot taken at the last update, always present
 * @param course          the live course, or null when it no longer exists
 * @param courseAvailable false when the course no longer exists (show "No longer available", no link)
 */
public record ProgressItemDto(
        UUID entryId,
        Long courseId,
        String courseTitle,
        String courseUrl,
        CourseSource courseSource,
        String courseSourceName,
        CourseDto course,
        boolean courseAvailable,
        ProgressStatus status,
        int progressPercent,
        String notes,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
