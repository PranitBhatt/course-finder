package com.coursefinder.repository;

import com.coursefinder.entity.CourseSource;
import com.coursefinder.entity.TopicFetch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicFetchRepository extends JpaRepository<TopicFetch, Long> {

    Optional<TopicFetch> findBySourceAndTopic(CourseSource source, String topic);
}
