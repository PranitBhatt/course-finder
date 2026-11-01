package com.coursefinder.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * A course in a search result, with its relevance score.
 * Serialized flat: all {@link CourseDto} fields plus {@code score}.
 */
public record CourseSearchResultDto(
        @JsonUnwrapped CourseDto course,
        double score
) {
}
