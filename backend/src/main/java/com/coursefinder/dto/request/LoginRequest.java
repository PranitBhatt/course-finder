package com.coursefinder.dto.request;

import com.coursefinder.util.LogMasking;
import jakarta.validation.constraints.NotBlank;

/**
 * JSON body of {@code POST /api/auth/login}.
 * No format checks here: a badly formed email simply won't match any account and gets the usual 401.
 */
public record LoginRequest(
        @NotBlank(message = "must not be blank")
        String email,

        @NotBlank(message = "must not be blank")
        String password
) {

    /**
     * A record's default toString prints every field - including the password. Spring prints request
     * bodies when its debug logging is turned on, so this keeps the password and full email out of logs.
     */
    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=****]".formatted(LogMasking.maskEmail(email));
    }
}
