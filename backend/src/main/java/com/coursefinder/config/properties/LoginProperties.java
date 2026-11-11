package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param maxFailedAttempts wrong passwords in a row before the account is locked
 * @param lockDuration      how long the lock lasts
 */
@ConfigurationProperties(prefix = "app.login")
public record LoginProperties(int maxFailedAttempts, Duration lockDuration) {
}
