package com.coursefinder.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body of {@code POST /api/auth/register}.
 * The max sizes match the app_user columns, so a too-long value is a 400 instead of a database error.
 */
public record RegisterRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = 255, message = "must be at most 255 characters")
        String email,

        // BCrypt only works with up to 72 bytes and rejects anything longer.
        @NotBlank(message = "must not be blank")
        @Size(max = 72, message = "must be at most 72 characters")
        String password,

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        String displayName
) {
}
