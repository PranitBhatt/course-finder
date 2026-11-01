package com.coursefinder.connector;

import java.util.List;

/**
 * A platform that can only be queried by topic (e.g. YouTube).
 * Results are fetched on demand and cached in the database.
 */
public interface TopicSearchConnector extends CourseConnector {

    /**
     * @param topic normalized user search topic, e.g. "machine learning"
     * @throws ConnectorException          if the platform call fails
     * @throws QuotaExceededException      if the platform's API quota is used up
     */
    List<ConnectorCourse> searchByTopic(String topic);
}
