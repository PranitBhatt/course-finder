package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * @param token value clients must send in {@code X-Admin-Token}; from ADMIN_TOKEN. Blank = admin locked.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String token) {

    public boolean hasToken() {
        return StringUtils.hasText(token);
    }

    /** Never print the token itself. */
    @Override
    public String toString() {
        return "AdminProperties[token=%s]".formatted(hasToken() ? "****" : "<missing>");
    }
}
