package com.coursefinder.config;

import com.coursefinder.config.properties.CorsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS as the very first filter, so every response - including a 429 from the rate limiter
 * or a 401 from the admin filter - carries the headers. Otherwise the browser would hide
 * the real error behind a generic CORS failure.
 * <p>
 * Allowed origins come from CORS_ALLOWED_ORIGINS (comma-separated).
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Retry-After", "X-RateLimit-Remaining"));
        config.setMaxAge(3600L);
        // Lets the frontend send and receive the refresh token cookie (fetch with credentials: "include").
        // Only safe because allowed origins is an exact list; browsers refuse it together with "*".
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
