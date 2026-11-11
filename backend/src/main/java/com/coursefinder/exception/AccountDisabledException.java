package com.coursefinder.exception;

/**
 * Mapped to 403 by {@link GlobalExceptionHandler}. Only thrown after the password was correct,
 * so it only tells the account owner something they're allowed to know.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("This account has been disabled");
    }
}
