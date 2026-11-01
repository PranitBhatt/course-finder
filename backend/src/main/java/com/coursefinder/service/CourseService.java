package com.coursefinder.service;

import com.coursefinder.dto.response.CourseDto;
import com.coursefinder.exception.ResourceNotFoundException;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.service.mapper.CourseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;

    public CourseService(CourseRepository courseRepository, CourseMapper courseMapper) {
        this.courseRepository = courseRepository;
        this.courseMapper = courseMapper;
    }

    @Transactional(readOnly = true)
    public CourseDto getById(long id) {
        return courseRepository.findById(id)
                .map(courseMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }
}
