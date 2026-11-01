package com.coursefinder.exception;

/**
 * The source can't be fully synced: it is on-demand only (YouTube) or disabled.
 * Mapped to 400 by {@link GlobalExceptionHandler}.
 */
public class UnsupportedSyncException extends RuntimeException {

    public UnsupportedSyncException(String message) {
        super(message);
    }
}
