package com.coursefinder.exception;

/**
 * Mapped to 401 by {@link GlobalExceptionHandler}.
 * Used for both "no such email" and "wrong password" so the response doesn't reveal
 * which emails have an account.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
