package com.coursefinder.config;

import com.coursefinder.config.properties.AdminProperties;
import com.coursefinder.config.properties.CorsProperties;
import com.coursefinder.config.properties.RateLimitProperties;
import com.coursefinder.security.AdminTokenFilter;
import com.coursefinder.security.OriginCheckFilter;
import com.coursefinder.security.SearchRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the admin, rate-limit and origin-check filters, each only on the paths it guards.
 * Order: CORS (CorsConfig) -> admin token -> rate limit -> origin check -> Spring MVC.
 * Rejected requests never reach a controller.
 */
@Configuration
@EnableConfigurationProperties({AdminProperties.class, RateLimitProperties.class})
public class ApiSecurityConfig {

    @Bean
    public FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(AdminProperties properties,
                                                                     ObjectMapper objectMapper) {
        FilterRegistrationBean<AdminTokenFilter> registration =
                new FilterRegistrationBean<>(new AdminTokenFilter(properties, objectMapper));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<SearchRateLimitFilter> searchRateLimitFilter(RateLimitProperties properties,
                                                                               ObjectMapper objectMapper) {
        FilterRegistrationBean<SearchRateLimitFilter> registration =
                new FilterRegistrationBean<>(new SearchRateLimitFilter(properties, objectMapper));
        registration.addUrlPatterns("/api/courses/search");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    /** CSRF protection for the two endpoints that use the refresh token cookie. */
    @Bean
    public FilterRegistrationBean<OriginCheckFilter> originCheckFilter(CorsProperties corsProperties,
                                                                       ObjectMapper objectMapper) {
        FilterRegistrationBean<OriginCheckFilter> registration =
                new FilterRegistrationBean<>(new OriginCheckFilter(corsProperties, objectMapper));
        registration.addUrlPatterns("/api/auth/refresh", "/api/auth/logout");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return registration;
    }
}
