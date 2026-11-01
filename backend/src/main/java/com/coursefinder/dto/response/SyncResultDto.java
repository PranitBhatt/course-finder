package com.coursefinder.dto.response;

import com.coursefinder.entity.CourseSource;

/** Response of {@code POST /api/admin/sync/{source}}. */
public record SyncResultDto(
        CourseSource source,
        String status,
        String message
) {
}
