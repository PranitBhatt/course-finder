package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.youtube")
public record YouTubeProperties(
        String apiKey,
        @DefaultValue("https://www.googleapis.com/youtube/v3") String baseUrl,
        @DefaultValue("25") int maxResults,
        @DefaultValue("7d") Duration cacheTtl,
        @DefaultValue("9000") int dailyQuotaUnits,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout
) {

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }

    /** Never print the key itself. */
    @Override
    public String toString() {
        return "YouTubeProperties[apiKey=%s, baseUrl=%s, maxResults=%d, cacheTtl=%s, dailyQuotaUnits=%d]"
                .formatted(hasApiKey() ? "****" : "<missing>", baseUrl, maxResults, cacheTtl, dailyQuotaUnits);
    }
}
