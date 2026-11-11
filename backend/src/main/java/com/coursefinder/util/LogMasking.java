package com.coursefinder.util;

/**
 * Hides personal data before it can end up in a log or a toString().
 * Logs are copied around, kept for months and read by many people, so they get only what's needed.
 */
public final class LogMasking {

    private LogMasking() {
    }

    /** {@code ada@example.com -> a***@example.com}. Enough to tell accounts apart, not to contact anyone. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
