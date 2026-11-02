package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * @param timeZone where a "day" starts and ends in the searches-per-day chart
 */
@ConfigurationProperties(prefix = "app.stats")
public record StatsProperties(
        @DefaultValue("UTC") ZoneId timeZone
) {
}
