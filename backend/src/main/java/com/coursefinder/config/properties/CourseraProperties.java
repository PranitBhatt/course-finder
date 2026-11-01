package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.coursera")
public record CourseraProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://api.coursera.org") String baseUrl,
        @DefaultValue("100") int pageSize,
        @DefaultValue("2000") int maxPages,
        @DefaultValue("2") int maxRetries,
        @DefaultValue("1s") Duration retryBackoff,
        @DefaultValue("0 0 3 * * *") String syncCron,
        @DefaultValue("true") boolean syncOnStartupIfEmpty,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout
) {
}
