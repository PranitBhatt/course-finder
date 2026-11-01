package com.coursefinder.dto.response;

import java.util.List;

/**
 * Paginated, ranked search results.
 *
 * @param fuzzyMatch true when no exact full-text match was found and results come from
 *                   typo-tolerant title matching (UI can show "showing similar results")
 */
public record CourseSearchResponse(
        String query,
        boolean fuzzyMatch,
        List<CourseSearchResultDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
