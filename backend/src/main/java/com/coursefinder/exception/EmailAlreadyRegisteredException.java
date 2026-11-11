package com.coursefinder.exception;

/** Mapped to 409 by {@link GlobalExceptionHandler}. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("An account with email %s already exists".formatted(email));
    }
}
