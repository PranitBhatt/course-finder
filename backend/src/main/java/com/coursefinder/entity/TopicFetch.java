package com.coursefinder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * Records when a topic was last fetched from an on-demand source, so we only call
 * the platform again once the cache TTL has passed.
 */
@Entity
@Table(name = "topic_fetch")
public class TopicFetch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CourseSource source;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    protected TopicFetch() {
        // for JPA
    }

    public TopicFetch(CourseSource source, String topic) {
        this.source = source;
        this.topic = topic;
    }

    public void recordFetch(int resultCount) {
        this.resultCount = resultCount;
        this.fetchedAt = Instant.now();
    }

    public boolean isFresh(Duration ttl) {
        return fetchedAt != null && fetchedAt.plus(ttl).isAfter(Instant.now());
    }

    public Long getId() {
        return id;
    }

    public CourseSource getSource() {
        return source;
    }

    public String getTopic() {
        return topic;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public int getResultCount() {
        return resultCount;
    }
}
