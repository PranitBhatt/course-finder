package com.coursefinder.dto.request;

import jakarta.validation.constraints.NotBlank;

/** JSON body of {@code POST /api/auth/refresh} and {@code POST /api/auth/logout}. */
public record RefreshTokenRequest(
        @NotBlank(message = "must not be blank")
        String refreshToken
) {
}
