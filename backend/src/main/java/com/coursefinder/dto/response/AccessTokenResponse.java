package com.coursefinder.dto.response;

/**
 * Returned by {@code POST /api/auth/refresh}. The refresh token itself doesn't change.
 *
 * @param accessToken new short-lived JWT
 * @param expiresIn   seconds until it expires
 */
public record AccessTokenResponse(
        String accessToken,
        long expiresIn
) {
}
