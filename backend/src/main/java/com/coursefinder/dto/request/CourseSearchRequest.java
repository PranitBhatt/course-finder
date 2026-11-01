package com.coursefinder.dto.request;

import com.coursefinder.entity.CourseSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of {@code GET /api/courses/search}.
 *
 * @param q      search text, e.g. "machine learning"
 * @param source optional platform filter (case-insensitive)
 * @param free   optional: true = only free, false = only not free (unknown is excluded from both)
 * @param page   zero-based page index
 * @param size   page size, 1-100
 */
public record CourseSearchRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String q,

        CourseSource source,

        Boolean free,

        @Min(value = 0, message = "must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "must be at least 1")
        @Max(value = 100, message = "must be at most 100")
        Integer size
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;

    public CourseSearchRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }
}
