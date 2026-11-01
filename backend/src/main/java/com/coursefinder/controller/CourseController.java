package com.coursefinder.controller;

import com.coursefinder.dto.request.CourseSearchRequest;
import com.coursefinder.dto.response.CourseDto;
import com.coursefinder.dto.response.CourseSearchResponse;
import com.coursefinder.service.CourseSearchService;
import com.coursefinder.service.CourseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseSearchService courseSearchService;
    private final CourseService courseService;

    public CourseController(CourseSearchService courseSearchService, CourseService courseService) {
        this.courseSearchService = courseSearchService;
        this.courseService = courseService;
    }

    /**
     * {@code GET /api/courses/search?q=machine learning&source=youtube&free=true&page=0&size=20}
     */
    @GetMapping("/search")
    public CourseSearchResponse search(@Valid @ModelAttribute CourseSearchRequest request) {
        return courseSearchService.search(request);
    }

    /** {@code GET /api/courses/42} */
    @GetMapping("/{id}")
    public CourseDto getById(@PathVariable @Positive(message = "must be a positive number") long id) {
        return courseService.getById(id);
    }
}
