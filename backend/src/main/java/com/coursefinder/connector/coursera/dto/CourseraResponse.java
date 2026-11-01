package com.coursefinder.connector.coursera.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response of {@code GET /api/courses.v1?start=&limit=&fields=&includes=partnerIds}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseraResponse(
        List<CourseraCourse> elements,
        Paging paging,
        Linked linked
) {

    public List<CourseraCourse> elementsOrEmpty() {
        return elements == null ? List.of() : elements;
    }

    public List<CourseraPartner> partnersOrEmpty() {
        return linked == null || linked.partners() == null ? List.of() : linked.partners();
    }

    /** @return start offset of the next page, or null if this is the last page */
    public Integer nextStart() {
        if (paging == null || paging.next() == null || paging.next().isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(paging.next().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Paging(String next, Integer total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Linked(@JsonProperty("partners.v1") List<CourseraPartner> partners) {
    }
}
