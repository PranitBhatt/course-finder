package com.coursefinder.dto.request;

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
}
