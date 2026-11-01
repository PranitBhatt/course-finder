package com.coursefinder.connector;

import com.coursefinder.entity.CourseSource;

/**
 * Base contract for every course platform integration.
 * <p>
 * A connector only talks to the platform and returns normalized {@link ConnectorCourse}s.
 * It never touches the database - persistence is handled by the service layer.
 * Implement {@link CatalogConnector} and/or {@link TopicSearchConnector} depending on
 * what the platform supports.
 */
public interface CourseConnector {

    CourseSource source();

    /** False when the connector is switched off or missing config (e.g. no API key). */
    boolean isEnabled();
}
