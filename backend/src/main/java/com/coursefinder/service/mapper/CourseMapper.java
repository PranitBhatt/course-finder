package com.coursefinder.service.mapper;

import com.coursefinder.dto.response.CourseDto;
import com.coursefinder.dto.response.CourseSearchResultDto;
import com.coursefinder.entity.Course;
import com.coursefinder.repository.projection.CourseSearchRow;
import org.springframework.stereotype.Component;

/** Converts entities and query rows into API DTOs. */
@Component
public class CourseMapper {

    public CourseDto toDto(Course course) {
        return new CourseDto(
                course.getId(),
                course.getSource(),
                course.getSource().getDisplayName(),
                course.getTitle(),
                course.getDescription(),
                course.getUrl(),
                course.getThumbnailUrl(),
                course.getProvider(),
                course.getLanguage(),
                course.getLevel(),
                course.getDurationText(),
                course.getFree(),
                course.getRating(),
                course.getLastSyncedAt()
        );
    }

    public CourseSearchResultDto toSearchResult(CourseSearchRow row) {
        CourseDto course = new CourseDto(
                row.id(),
                row.source(),
                row.source().getDisplayName(),
                row.title(),
                row.description(),
                row.url(),
                row.thumbnailUrl(),
                row.provider(),
                row.language(),
                row.level(),
                row.durationText(),
                row.free(),
                row.rating(),
                row.lastSyncedAt()
        );
        return new CourseSearchResultDto(course, round(row.rank()));
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
