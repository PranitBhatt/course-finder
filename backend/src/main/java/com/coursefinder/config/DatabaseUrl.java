package com.coursefinder.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Converts a hosting-style database URL into JDBC settings.
 * <p>
 * Neon, Render, Heroku and others hand out URLs like
 * {@code postgresql://user:pass@host:5432/dbname?sslmode=require}, but the PostgreSQL JDBC driver
 * needs {@code jdbc:postgresql://host:5432/dbname?sslmode=require} with the credentials separate.
 * SSL is always required: these databases are reached over the public internet.
 *
 * @param jdbcUrl  e.g. {@code jdbc:postgresql://host:5432/dbname?sslmode=require}
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    private static final int DEFAULT_PORT = 5432;

    public static DatabaseUrl parse(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is empty");
        }

        URI uri;
        try {
            uri = new URI(databaseUrl.trim());
        } catch (URISyntaxException e) {
            // Don't include the URL itself: it contains the password.
            throw new IllegalArgumentException("DATABASE_URL is not a valid URL");
        }

        String scheme = uri.getScheme();
        if (!"postgres".equals(scheme) && !"postgresql".equals(scheme)) {
            throw new IllegalArgumentException("DATABASE_URL must start with postgres:// or postgresql://");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() == null) {
            throw new IllegalArgumentException("DATABASE_URL must contain user, password and host");
        }

        String[] credentials = uri.getRawUserInfo().split(":", 2);
        if (credentials.length != 2) {
            throw new IllegalArgumentException("DATABASE_URL must contain a password");
        }

        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (database.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL must contain a database name");
        }

        int port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();
        String jdbcUrl = "jdbc:postgresql://%s:%d/%s?sslmode=require".formatted(uri.getHost(), port, database);

        return new DatabaseUrl(jdbcUrl, decode(credentials[0]), decode(credentials[1]));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** Never print the password. */
    @Override
    public String toString() {
        return "DatabaseUrl[jdbcUrl=%s, username=%s, password=****]".formatted(jdbcUrl, username);
    }
}
