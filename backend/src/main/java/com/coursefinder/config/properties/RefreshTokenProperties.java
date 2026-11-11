package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param expiration how long a refresh token can be used to get new access tokens
 */
@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(Duration expiration) {
}
