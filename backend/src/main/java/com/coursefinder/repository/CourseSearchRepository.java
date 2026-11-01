package com.coursefinder.repository;

import com.coursefinder.entity.CourseSource;
import com.coursefinder.repository.projection.CourseSearchRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Ranked course search backed by PostgreSQL full-text search and pg_trgm.
 */
public interface CourseSearchRepository {

    /**
     * Full-text search on title + description, ranked with {@code ts_rank}.
     * Supports web-style syntax: {@code "exact phrase"}, {@code -exclude}, {@code or}.
     *
     * @param source optional filter, null = all sources
     * @param free   optional filter, null = any
     */
    Page<CourseSearchRow> fullTextSearch(String query, CourseSource source, Boolean free, Pageable pageable);

    /**
     * Typo-tolerant title search using trigram word similarity ({@code <%} operator).
     * Used as a fallback when full-text search finds nothing (e.g. "machne lerning").
     */
    Page<CourseSearchRow> fuzzyTitleSearch(String query, CourseSource source, Boolean free, Pageable pageable);
}
