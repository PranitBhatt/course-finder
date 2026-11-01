package com.coursefinder.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Platforms courses are ingested from. Stored as VARCHAR (see ck_course_source in V2).
 * Adding a source = new constant + new migration updating the CHECK constraint + a connector.
 */
public enum CourseSource {

    COURSERA("Coursera"),
    YOUTUBE("YouTube");

    private final String displayName;

    CourseSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Case-insensitive lookup, so both "youtube" and "YOUTUBE" work in URLs. */
    public static Optional<CourseSource> fromString(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(source -> source.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
