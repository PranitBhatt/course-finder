package com.coursefinder.config;

import com.coursefinder.entity.CourseSource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Lets request params and path variables use any case: {@code ?source=youtube}, {@code /sync/COURSERA}.
 * Spring Boot registers Converter beans with MVC automatically.
 */
@Component
public class StringToCourseSourceConverter implements Converter<String, CourseSource> {

    @Override
    public CourseSource convert(String value) {
        if (value.isBlank()) {
            return null;
        }
        return CourseSource.fromString(value)
                .orElseThrow(() -> new IllegalArgumentException("Unknown source: " + value));
    }
}
