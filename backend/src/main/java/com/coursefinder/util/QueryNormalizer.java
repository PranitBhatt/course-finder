package com.coursefinder.util;

import java.util.Locale;

/** One canonical form for search text, so "  Machine   LEARNING" and "machine learning" match. */
public final class QueryNormalizer {

    public static final int MAX_LENGTH = 200;

    private QueryNormalizer() {
    }

    /** Lowercases, trims, collapses whitespace and caps the length. */
    public static String normalize(String query) {
        String normalized = query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_LENGTH ? normalized.substring(0, MAX_LENGTH) : normalized;
    }
}
