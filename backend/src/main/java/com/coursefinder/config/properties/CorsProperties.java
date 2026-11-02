package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * @param allowedOrigins exact origins, e.g. https://mydomain.me. Bound from a comma-separated
 *                       CORS_ALLOWED_ORIGINS value.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins
) {
}
