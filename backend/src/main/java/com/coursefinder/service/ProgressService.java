package com.coursefinder.service;

import com.coursefinder.dto.request.ProgressUpdateRequest;
import com.coursefinder.dto.response.ProgressItemDto;
import com.coursefinder.dto.response.ProgressPageResponse;
import com.coursefinder.dto.response.ProgressStatusDto;
import com.coursefinder.dto.response.ProgressSummaryDto;
import com.coursefinder.entity.AppUser;
import com.coursefinder.entity.Course;
import com.coursefinder.entity.ProgressStatus;
import com.coursefinder.entity.UserCourseProgress;
import com.coursefinder.exception.FeatureNotAvailableException;
import com.coursefinder.exception.ResourceNotFoundException;
import com.coursefinder.repository.AppUserRepository;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.repository.UserCourseProgressRepository;
import com.coursefinder.service.mapper.CourseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Course progress ("My Learning") for logged-in users.
 * <p>
 * Every public method takes the user's public id from the verified token (never from the request
 * body or URL) and starts with {@link #requireUser}, which also asks {@link EntitlementService}.
 * All queries are filtered by that user's id, so nobody can read or change someone else's rows.
 */
@Service
public class ProgressService {

    // Newest change first; id breaks ties so paging is stable when two rows share a timestamp.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final UserCourseProgressRepository progressRepository;
    private final AppUserRepository appUserRepository;
    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;
    private final EntitlementService entitlementService;

    public ProgressService(UserCourseProgressRepository progressRepository, AppUserRepository appUserRepository,
                           CourseRepository courseRepository, CourseMapper courseMapper,
                           EntitlementService entitlementService) {
        this.progressRepository = progressRepository;
        this.appUserRepository = appUserRepository;
        this.courseRepository = courseRepository;
        this.courseMapper = courseMapper;
        this.entitlementService = entitlementService;
    }

    @Transactional(readOnly = true)
    public ProgressPageResponse list(UUID userPublicId, ProgressStatus status, int page, int size) {
        AppUser user = requireUser(userPublicId);
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<UserCourseProgress> rows = status == null
                ? progressRepository.findByUserId(user.getId(), pageable)
                : progressRepository.findByUserIdAndStatus(user.getId(), status, pageable);

        // Live course data for the whole page in ONE query, not one per row.
        List<Long> courseIds = rows.getContent().stream()
                .map(UserCourseProgress::getCourseId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Course> courses = courseIds.isEmpty() ? Map.of()
                : courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        List<ProgressItemDto> content = rows.getContent().stream()
                .map(row -> toDto(row, row.getCourseId() == null ? null : courses.get(row.getCourseId())))
                .toList();
        return new ProgressPageResponse(content, rows.getNumber(), rows.getSize(),
                rows.getTotalElements(), rows.getTotalPages());
    }

    /**
     * Status for just the given courses: search result cards ask once per results page with all
     * their ids, instead of each card asking for itself (12 cards would be 12 requests: N+1).
     * Courses the user doesn't track are simply absent from the map.
     */
    @Transactional(readOnly = true)
    public Map<Long, ProgressStatusDto> lookup(UUID userPublicId, Collection<Long> courseIds) {
        AppUser user = requireUser(userPublicId);
        Map<Long, ProgressStatusDto> result = new LinkedHashMap<>();
        for (UserCourseProgress row : progressRepository.findByUserIdAndCourseIdIn(user.getId(), courseIds)) {
            result.put(row.getCourseId(), new ProgressStatusDto(row.getStatus(), row.getProgressPercent()));
        }
        return result;
    }

    /**
     * Creates or updates the entry for a course (idempotent: the same request twice gives the same
     * result). 404 if the course doesn't exist. updated_at only moves when something really changed.
     */
    @Transactional
    public ProgressItemDto upsert(UUID userPublicId, long courseId, ProgressUpdateRequest request) {
        AppUser user = requireUser(userPublicId);
        // A row that still has this course_id means the course exists (otherwise the FK would have
        // nulled it), so this check is only ever false when creating.
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        // Microseconds: what PostgreSQL stores. The response then matches what a later read returns.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        progressRepository.insertIfAbsent(user.getId(), courseId, course.getTitle(), course.getUrl(),
                course.getSource().name(), now);
        UserCourseProgress progress = progressRepository.findByUserIdAndCourseId(user.getId(), courseId)
                .orElseThrow(() -> new IllegalStateException("Progress row missing right after insert"));

        UserCourseProgress.State before = progress.state();
        progress.refreshSnapshot(course);
        applyUpdate(progress, request, now);
        if (!progress.state().equals(before)) {
            progress.setUpdatedAt(now);
        }
        // Managed entity: Hibernate writes the changes when the transaction commits.
        return toDto(progress, course);
    }

    /** Idempotent: removing an entry that isn't there is not an error. */
    @Transactional
    public void delete(UUID userPublicId, long courseId) {
        AppUser user = requireUser(userPublicId);
        progressRepository.deleteByUserIdAndCourseId(user.getId(), courseId);
    }

    /** Same as {@link #delete} but by entry id, for entries whose course is gone (courseId is null). */
    @Transactional
    public void deleteEntry(UUID userPublicId, UUID entryId) {
        AppUser user = requireUser(userPublicId);
        progressRepository.deleteByUserIdAndPublicId(user.getId(), entryId);
    }

    @Transactional(readOnly = true)
    public ProgressSummaryDto summary(UUID userPublicId) {
        AppUser user = requireUser(userPublicId);
        Map<ProgressStatus, Long> counts = new EnumMap<>(ProgressStatus.class);
        progressRepository.countByStatus(user.getId())
                .forEach(row -> counts.put(row.getStatus(), row.getCount()));

        long planned = counts.getOrDefault(ProgressStatus.PLANNED, 0L);
        long inProgress = counts.getOrDefault(ProgressStatus.IN_PROGRESS, 0L);
        long completed = counts.getOrDefault(ProgressStatus.COMPLETED, 0L);
        return new ProgressSummaryDto(planned, inProgress, completed, planned + inProgress + completed);
    }

    /**
     * The status rules. Kept here (not in the controller) so every way of changing progress
     * follows them. started_at is only ever set, never cleared: once started, always started.
     */
    static void applyUpdate(UserCourseProgress progress, ProgressUpdateRequest request, Instant now) {
        ProgressStatus previous = progress.getStatus();
        ProgressStatus next = request.status();

        switch (next) {
            case PLANNED -> progress.setProgressPercent(
                    request.progressPercent() != null ? request.progressPercent() : 0);
            case IN_PROGRESS -> {
                if (request.progressPercent() != null) {
                    progress.setProgressPercent(request.progressPercent());
                }
                if (progress.getStartedAt() == null) {
                    progress.setStartedAt(now);
                }
            }
            case COMPLETED -> {
                progress.setProgressPercent(100);
                if (progress.getStartedAt() == null) {
                    progress.setStartedAt(now);
                }
                // Repeating "completed" keeps the original date, so a retried request changes nothing.
                if (previous != ProgressStatus.COMPLETED || progress.getCompletedAt() == null) {
                    progress.setCompletedAt(now);
                }
            }
        }
        if (next != ProgressStatus.COMPLETED) {
            progress.setCompletedAt(null);
        }
        progress.setStatus(next);

        if (request.notes() != null) {
            String notes = request.notes().strip();
            progress.setNotes(notes.isEmpty() ? null : notes);
        }
    }

    private AppUser requireUser(UUID publicId) {
        AppUser user = appUserRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("User", publicId));
        if (!entitlementService.hasAccess(user, Feature.PROGRESS_TRACKING)) {
            throw new FeatureNotAvailableException(Feature.PROGRESS_TRACKING);
        }
        return user;
    }

    private ProgressItemDto toDto(UserCourseProgress row, Course course) {
        return new ProgressItemDto(
                row.getPublicId(),
                row.getCourseId(),
                row.getCourseTitle(),
                row.getCourseUrl(),
                row.getCourseSource(),
                row.getCourseSource().getDisplayName(),
                course == null ? null : courseMapper.toDto(course),
                course != null,
                row.getStatus(),
                row.getProgressPercent(),
                row.getNotes(),
                row.getStartedAt(),
                row.getCompletedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
