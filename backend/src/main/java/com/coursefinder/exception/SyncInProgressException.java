package com.coursefinder.exception;

import com.coursefinder.entity.CourseSource;

/** Mapped to 409 by {@link GlobalExceptionHandler}. */
public class SyncInProgressException extends RuntimeException {

    public SyncInProgressException(CourseSource source) {
        super("A sync for %s is already running".formatted(source.getDisplayName()));
    }
}
