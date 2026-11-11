package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param absoluteLifetime how long a login lasts at most, no matter how often it is refreshed
 */
@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(Duration absoluteLifetime) {
}
