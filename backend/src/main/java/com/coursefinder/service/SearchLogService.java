package com.coursefinder.service;

import com.coursefinder.util.QueryNormalizer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Records searches in {@code search_log} on a background thread, so logging never slows down
 * or breaks a search. A failed insert is logged and dropped.
 */
@Service
public class SearchLogService {

    private static final Logger log = LoggerFactory.getLogger(SearchLogService.class);

    private static final String INSERT_SQL = "INSERT INTO search_log (query, result_count) VALUES (?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SearchLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void logSearchAsync(String query, long resultCount) {
        String normalized = QueryNormalizer.normalize(query);
        int count = (int) Math.min(resultCount, Integer.MAX_VALUE);

        executor.execute(() -> {
            try {
                jdbcTemplate.update(INSERT_SQL, normalized, count);
            } catch (RuntimeException e) {
                log.warn("Could not record search '{}': {}", normalized, e.getMessage());
            }
        });
    }

    /** Lets in-flight inserts finish on shutdown (Render sends SIGTERM before stopping). */
    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
