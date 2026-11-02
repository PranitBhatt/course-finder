package com.coursefinder.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Prod only: builds the DataSource from the {@code DATABASE_URL} environment variable
 * (the connection string Neon gives you). Pool settings still come from
 * {@code spring.datasource.hikari.*} in application-prod.yml. Flyway uses this DataSource too.
 */
@Configuration
@Profile("prod")
public class DatabaseUrlConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUrlConfig.class);

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties, Environment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "DATABASE_URL is not set. Copy the connection string from Neon into the service's environment.");
        }

        DatabaseUrl parsed = DatabaseUrl.parse(databaseUrl);
        log.info("Using database {}", parsed.jdbcUrl());

        properties.setUrl(parsed.jdbcUrl());
        properties.setUsername(parsed.username());
        properties.setPassword(parsed.password());
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
