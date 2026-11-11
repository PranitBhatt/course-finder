package com.coursefinder.exception;

/**
 * Mapped to 401 by {@link GlobalExceptionHandler}.
 * Same message for unknown, expired and logged-out tokens: the client has to log in again either way.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
