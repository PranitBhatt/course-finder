package com.coursefinder.dto.request;

import com.coursefinder.util.LogMasking;
import com.coursefinder.validation.MaxUtf8Bytes;
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

        // At least 8 characters: shorter passwords fall to guessing quickly, even with BCrypt.
        // At most 72 BYTES: BCrypt only looks at the first 72 bytes of the password and Spring refuses
        // anything longer. It's bytes, not characters - "é" takes 2 bytes, an emoji 4 - so @Size
        // (which counts characters) can't check this on its own.
        @NotBlank(message = "must not be blank")
        @Size(min = 8, message = "must be at least 8 characters")
        @MaxUtf8Bytes(value = 72, message = "must be at most 72 bytes (fewer characters if you use accents or emoji)")
        String password,

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        String displayName
) {

    /** Keeps the password and full email out of logs (see LoginRequest). */
    @Override
    public String toString() {
        return "RegisterRequest[email=%s, password=****, displayName=%s]"
                .formatted(LogMasking.maskEmail(email), displayName);
    }
}
