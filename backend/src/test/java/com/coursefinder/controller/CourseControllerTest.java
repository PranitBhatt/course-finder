package com.coursefinder.controller;

import com.coursefinder.config.CorsConfig;
import com.coursefinder.dto.request.CourseSearchRequest;
import com.coursefinder.dto.response.CourseDto;
import com.coursefinder.dto.response.CourseSearchResponse;
import com.coursefinder.dto.response.CourseSearchResultDto;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.exception.ResourceNotFoundException;
import com.coursefinder.service.CourseSearchService;
import com.coursefinder.service.CourseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: request binding, validation, error format, CORS. Services are mocked. */
@WebMvcTest(CourseController.class)
@Import(CorsConfig.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseSearchService courseSearchService;
    @MockitoBean
    private CourseService courseService;

    private static CourseDto sampleCourse() {
        return new CourseDto(1L, CourseSource.YOUTUBE, "YouTube", "Machine Learning Full Course", "desc",
                "https://www.youtube.com/playlist?list=PL1", null, "freeCodeCamp.org", "en", null,
                "42 videos", true, null, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void searchReturnsFlatResultsWithScore() throws Exception {
        CourseSearchResponse response = new CourseSearchResponse("machine learning", false,
                List.of(new CourseSearchResultDto(sampleCourse(), 0.75)), 0, 20, 1, 1);
        when(courseSearchService.search(any())).thenReturn(response);

        mockMvc.perform(get("/api/courses/search").param("q", "machine learning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Machine Learning Full Course"))
                .andExpect(jsonPath("$.content[0].source").value("YOUTUBE"))
                .andExpect(jsonPath("$.content[0].score").value(0.75))
                .andExpect(jsonPath("$.content[0].course").doesNotExist());
    }

    @Test
    void searchAppliesDefaultsAndCaseInsensitiveSource() throws Exception {
        when(courseSearchService.search(any()))
                .thenReturn(new CourseSearchResponse("java", false, List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/courses/search").param("q", "java").param("source", "youtube"))
                .andExpect(status().isOk());

        ArgumentCaptor<CourseSearchRequest> captor = ArgumentCaptor.forClass(CourseSearchRequest.class);
        verify(courseSearchService).search(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(CourseSource.YOUTUBE);
        assertThat(captor.getValue().page()).isZero();
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    void missingQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/courses/search"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.q").value("must not be blank"));

        verify(courseSearchService, never()).search(any());
    }

    @Test
    void outOfRangePagingIsRejected() throws Exception {
        mockMvc.perform(get("/api/courses/search").param("q", "java").param("size", "500").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").value("must be at most 100"))
                .andExpect(jsonPath("$.errors.page").value("must be 0 or greater"));
    }

    @Test
    void unknownSourceIsRejected() throws Exception {
        mockMvc.perform(get("/api/courses/search").param("q", "java").param("source", "udemy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.source").value("invalid value 'udemy'"));
    }

    @Test
    void nonNumericPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/courses/search").param("q", "java").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").value("invalid value 'abc'"));
    }

    @Test
    void getByIdReturnsCourse() throws Exception {
        when(courseService.getById(1L)).thenReturn(sampleCourse());

        mockMvc.perform(get("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sourceName").value("YouTube"));
    }

    @Test
    void unknownIdReturns404Problem() throws Exception {
        when(courseService.getById(99L)).thenThrow(new ResourceNotFoundException("Course", 99L));

        mockMvc.perform(get("/api/courses/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Course with id 99 not found"));
    }

    @Test
    void nonNumericIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/courses/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").value("invalid value 'abc'"));
    }

    @Test
    void negativeIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/courses/-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").value("must be a positive number"));
    }

    @Test
    void corsAllowsViteDevServer() throws Exception {
        mockMvc.perform(options("/api/courses/search")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsRejectsOtherOrigins() throws Exception {
        mockMvc.perform(options("/api/courses/search")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
