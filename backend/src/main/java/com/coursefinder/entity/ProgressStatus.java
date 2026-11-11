package com.coursefinder.entity;

/** Where a user is with a course. Stored as text (see ck_user_course_progress_status in V12). */
public enum ProgressStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED
}
