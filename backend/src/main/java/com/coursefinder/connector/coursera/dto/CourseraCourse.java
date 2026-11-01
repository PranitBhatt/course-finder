package com.coursefinder.connector.coursera.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** One element of {@code GET /api/courses.v1}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseraCourse(
        String id,
        String slug,
        String name,
        String description,
        String photoUrl,
        String workload,
        List<String> primaryLanguages,
        List<String> partnerIds
) {
}
