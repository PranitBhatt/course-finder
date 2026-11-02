package com.coursefinder.security;

import com.coursefinder.config.properties.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Limits {@code /api/courses/search} to N requests per minute per client IP, so nobody can
 * burn the YouTube API quota by hammering the search box. Over the limit: 429 + Retry-After.
 * <p>
 * Buckets live in memory, one per IP, and are dropped after 10 idle minutes (max 50k IPs),
 * so memory stays flat. The client IP comes from {@code X-Forwarded-For} via Tomcat's
 * RemoteIpValve ({@code server.forward-headers-strategy=native} in prod).
 */
public class SearchRateLimitFilter extends OncePerRequestFilter {

    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final int requestsPerMinute;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    public SearchRateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.requestsPerMinute = properties.searchRequestsPerMinute();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return requestsPerMinute <= 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = buckets.get(request.getRemoteAddr(), ip -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader(REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        ProblemResponses.write(response, objectMapper, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                "Search limit is %d per minute. Try again in %d seconds."
                        .formatted(requestsPerMinute, retryAfterSeconds));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
