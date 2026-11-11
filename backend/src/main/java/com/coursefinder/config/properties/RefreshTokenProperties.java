package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param expiration        how long a refresh token can be used to get new access tokens
 * @param cookieSecure      whether the refresh token cookie is only sent over HTTPS (false for local http)
 * @param reuseGracePeriod  a used token showing up again within this time is treated as a harmless
 *                          race (two tabs refreshing at once) instead of theft
 */
@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(Duration expiration, boolean cookieSecure, Duration reuseGracePeriod) {
}
