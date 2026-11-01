package com.coursefinder.connector;

import com.coursefinder.entity.CourseSource;

/**
 * Any failure talking to an external platform: network errors, bad status codes,
 * unexpected payloads. Callers decide whether to fail or degrade gracefully.
 */
public class ConnectorException extends RuntimeException {

    private final CourseSource source;

    public ConnectorException(CourseSource source, String message) {
        super(message);
        this.source = source;
    }

    public ConnectorException(CourseSource source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    public CourseSource getSource() {
        return source;
    }
}
