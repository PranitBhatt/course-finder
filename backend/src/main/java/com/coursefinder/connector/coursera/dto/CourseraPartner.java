package com.coursefinder.connector.coursera.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A university/company offering the course, from {@code linked["partners.v1"]}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseraPartner(
        String id,
        String name
) {
}
