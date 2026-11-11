package com.coursefinder.exception;

/** Mapped to 409 by {@link GlobalExceptionHandler}. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        // No email in the message: exception messages can end up in logs.
        super("An account with this email already exists");
    }
}
