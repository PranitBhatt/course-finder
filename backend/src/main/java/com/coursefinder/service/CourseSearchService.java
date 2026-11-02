package com.coursefinder.service;

import com.coursefinder.dto.request.CourseSearchRequest;
import com.coursefinder.dto.response.CourseSearchResponse;
import com.coursefinder.dto.response.CourseSearchResultDto;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.repository.projection.CourseSearchRow;
import com.coursefinder.service.mapper.CourseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Search flow:
 * <ol>
 *   <li>Refresh on-demand sources (YouTube) for this topic if their cache is stale</li>
 *   <li>Full-text search in Postgres, ranked by ts_rank</li>
 *   <li>If nothing matched, fall back to typo-tolerant trigram search on titles</li>
 * </ol>
 */
@Service
public class CourseSearchService {

    private final CourseRepository courseRepository;
    private final TopicFetchService topicFetchService;
    private final CourseMapper courseMapper;
    private final SearchLogService searchLogService;

    public CourseSearchService(CourseRepository courseRepository,
                               TopicFetchService topicFetchService,
                               CourseMapper courseMapper,
                               SearchLogService searchLogService) {
        this.courseRepository = courseRepository;
        this.topicFetchService = topicFetchService;
        this.courseMapper = courseMapper;
        this.searchLogService = searchLogService;
    }

    public CourseSearchResponse search(CourseSearchRequest request) {
        String query = request.q().trim();
        CourseSource source = request.source();
        Boolean free = request.free();
        Pageable pageable = PageRequest.of(request.page(), request.size());

        if (shouldRefreshOnDemandSources(free)) {
            topicFetchService.ensureFresh(query, source);
        }

        boolean fuzzyMatch = false;
        Page<CourseSearchRow> page = courseRepository.fullTextSearch(query, source, free, pageable);
        if (page.getTotalElements() == 0) {
            page = courseRepository.fuzzyTitleSearch(query, source, free, pageable);
            fuzzyMatch = page.getTotalElements() > 0;
        }

        List<CourseSearchResultDto> content = page.getContent().stream()
                .map(courseMapper::toSearchResult)
                .toList();

        // Count each search once, not once per results page the user clicks through.
        if (request.page() == 0) {
            searchLogService.logSearchAsync(query, page.getTotalElements());
        }

        return new CourseSearchResponse(
                query,
                fuzzyMatch,
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /** On-demand sources are all free, so a "paid only" search can't use their results. */
    private static boolean shouldRefreshOnDemandSources(Boolean free) {
        return !Boolean.FALSE.equals(free);
    }
}
