package com.coursefinder.connector;

import com.coursefinder.entity.CourseSource;

/**
 * The platform's API quota is used up (either our own budget or the platform said so).
 */
public class QuotaExceededException extends ConnectorException {

    public QuotaExceededException(CourseSource source, String message) {
        super(source, message);
    }
}
