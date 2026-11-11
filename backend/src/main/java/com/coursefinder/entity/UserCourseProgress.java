package com.coursefinder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One user's progress on one course.
 * <p>
 * Rows are created by {@code UserCourseProgressRepository.insertIfAbsent} (an INSERT ... ON CONFLICT),
 * so this entity has no public constructor and Hibernate never inserts it. The status rules live in
 * {@link com.coursefinder.service.ProgressService}; this class only holds the data.
 * <p>
 * userId and courseId are plain columns, not relationships, like {@link UserSession}: we never need
 * to walk from a progress row to a whole user, and the course may be gone (courseId NULL).
 */
@Entity
@Table(name = "user_course_progress")
public class UserCourseProgress {

    @Id
    private Long id;

    @Column(name = "public_id", nullable = false, insertable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private Long userId;

    // Set to NULL by the database when a sync deletes the course; never written by us.
    @Column(name = "course_id", insertable = false, updatable = false)
    private Long courseId;

    @Column(name = "course_title", nullable = false, columnDefinition = "text")
    private String courseTitle;

    @Column(name = "course_url", nullable = false, columnDefinition = "text")
    private String courseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_source", nullable = false, length = 32)
    private CourseSource courseSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProgressStatus status;

    @Column(name = "progress_percent", nullable = false)
    private short progressPercent;

    @Column(length = 500)
    private String notes;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserCourseProgress() {
        // for JPA
    }

    /** Everything a user (or a snapshot refresh) can change, to tell whether an update changed anything. */
    public record State(String courseTitle, String courseUrl, CourseSource courseSource, ProgressStatus status,
                        short progressPercent, String notes, Instant startedAt, Instant completedAt) {
    }

    public State state() {
        return new State(courseTitle, courseUrl, courseSource, status, progressPercent, notes, startedAt, completedAt);
    }

    public void refreshSnapshot(Course course) {
        this.courseTitle = course.getTitle();
        this.courseUrl = course.getUrl();
        this.courseSource = course.getSource();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public String getCourseUrl() {
        return courseUrl;
    }

    public CourseSource getCourseSource() {
        return courseSource;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public void setStatus(ProgressStatus status) {
        this.status = status;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = (short) progressPercent;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
