package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param searchRequestsPerMinute allowed searches per client IP per minute; 0 disables the limit
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("60") int searchRequestsPerMinute
) {
}
