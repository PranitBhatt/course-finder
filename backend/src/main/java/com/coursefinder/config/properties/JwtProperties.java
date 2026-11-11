package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param secret                HMAC key for signing tokens; from JWT_SECRET. Must be at least 32 bytes for HS256.
 * @param accessTokenExpiration how long an access token stays valid
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenExpiration) {

    /** Never print the secret itself. */
    @Override
    public String toString() {
        return "JwtProperties[secret=****, accessTokenExpiration=%s]".formatted(accessTokenExpiration);
    }
}
