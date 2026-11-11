package com.coursefinder.exception;

import java.time.Duration;

/** Mapped to 429 with a Retry-After header by {@link GlobalExceptionHandler}. */
public class AccountLockedException extends RuntimeException {

    private final Duration retryAfter;

    public AccountLockedException(Duration retryAfter) {
        super("Too many failed login attempts. Try again later.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
