package com.coursefinder.dto.response;

import java.util.List;

/** A page of "My Learning" entries, newest change first. */
public record ProgressPageResponse(
        List<ProgressItemDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
