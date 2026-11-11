package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param secret     HMAC key for signing tokens; from JWT_SECRET. Must be at least 32 bytes for HS256.
 * @param expiration how long a token stays valid after login
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {

    /** Never print the secret itself. */
    @Override
    public String toString() {
        return "JwtProperties[secret=****, expiration=%s]".formatted(expiration);
    }
}
