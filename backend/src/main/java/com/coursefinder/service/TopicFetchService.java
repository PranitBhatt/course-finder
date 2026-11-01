package com.coursefinder.service;

import com.coursefinder.config.properties.YouTubeProperties;
import com.coursefinder.connector.ConnectorCourse;
import com.coursefinder.connector.ConnectorException;
import com.coursefinder.connector.QuotaExceededException;
import com.coursefinder.connector.TopicSearchConnector;
import com.coursefinder.entity.CourseSource;
import com.coursefinder.entity.TopicFetch;
import com.coursefinder.repository.CourseRepository;
import com.coursefinder.repository.TopicFetchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps on-demand sources (YouTube) cached per topic.
 * <p>
 * Before a search, {@link #ensureFresh} checks {@code topic_fetch}: if the topic was fetched
 * within the cache TTL nothing happens, otherwise the platform is called and results are upserted.
 * Platform failures never fail the user's search - they are logged and the cached data is used.
 */
@Service
public class TopicFetchService {

    private static final Logger log = LoggerFactory.getLogger(TopicFetchService.class);

    /** After a failure, don't call that platform again for this long (saves quota on e.g. a bad key). */
    static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(5);
    private static final int LOCK_STRIPES = 64;

    private final List<TopicSearchConnector> connectors;
    private final TopicFetchRepository topicFetchRepository;
    private final CourseRepository courseRepository;
    private final Duration cacheTtl;

    private final Map<CourseSource, Instant> cooldownUntil = new ConcurrentHashMap<>();
    // Stops two concurrent searches for the same topic from both calling the API.
    private final ReentrantLock[] topicLocks = new ReentrantLock[LOCK_STRIPES];

    public TopicFetchService(List<TopicSearchConnector> connectors,
                             TopicFetchRepository topicFetchRepository,
                             CourseRepository courseRepository,
                             YouTubeProperties youTubeProperties) {
        this.connectors = connectors;
        this.topicFetchRepository = topicFetchRepository;
        this.courseRepository = courseRepository;
        this.cacheTtl = youTubeProperties.cacheTtl();
        for (int i = 0; i < LOCK_STRIPES; i++) {
            topicLocks[i] = new ReentrantLock();
        }
    }

    /**
     * Lowercases, trims and collapses whitespace so "  Machine   Learning" and
     * "machine learning" share one cache entry.
     */
    public static String normalizeTopic(String topic) {
        return topic.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Makes sure every enabled on-demand source has recent results for {@code topic}.
     *
     * @param sourceFilter only refresh this source, or null for all on-demand sources
     */
    public void ensureFresh(String topic, CourseSource sourceFilter) {
        String normalizedTopic = normalizeTopic(topic);
        for (TopicSearchConnector connector : connectors) {
            boolean selected = sourceFilter == null || connector.source() == sourceFilter;
            if (selected && connector.isEnabled() && !isCoolingDown(connector.source())) {
                refreshIfStale(connector, normalizedTopic);
            }
        }
    }

    private void refreshIfStale(TopicSearchConnector connector, String topic) {
        ReentrantLock lock = topicLocks[Math.floorMod((connector.source() + topic).hashCode(), LOCK_STRIPES)];
        lock.lock();
        try {
            TopicFetch fetch = topicFetchRepository.findBySourceAndTopic(connector.source(), topic)
                    .orElseGet(() -> new TopicFetch(connector.source(), topic));
            if (fetch.isFresh(cacheTtl)) {
                return;
            }

            List<ConnectorCourse> courses = connector.searchByTopic(topic);
            courseRepository.upsertAll(courses, Instant.now().truncatedTo(ChronoUnit.MICROS));
            fetch.recordFetch(courses.size());
            topicFetchRepository.save(fetch);
            log.info("Fetched {} {} results for '{}'", courses.size(), connector.source(), topic);
        } catch (QuotaExceededException e) {
            log.warn("{} quota exhausted, serving cached results for '{}'", connector.source(), topic);
        } catch (ConnectorException e) {
            cooldownUntil.put(connector.source(), Instant.now().plus(FAILURE_COOLDOWN));
            log.warn("{} fetch failed for '{}', pausing calls for {}: {}",
                    connector.source(), topic, FAILURE_COOLDOWN, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private boolean isCoolingDown(CourseSource source) {
        Instant until = cooldownUntil.get(source);
        return until != null && Instant.now().isBefore(until);
    }
}
