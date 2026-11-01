package com.coursefinder.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param fuzzyThreshold minimum pg_trgm word similarity (0-1) for typo-tolerant matches.
 *                       Measured: one-letter typos score ~0.5-0.6, unrelated titles &lt;= 0.25.
 */
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        @DefaultValue("0.4") double fuzzyThreshold
) {
}
