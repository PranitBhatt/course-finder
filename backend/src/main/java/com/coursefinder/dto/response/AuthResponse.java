package com.coursefinder.dto.response;

/**
 * Returned by register and login.
 *
 * @param accessToken  short-lived JWT to send as {@code Authorization: Bearer <token>}
 * @param refreshToken long-lived token for {@code POST /api/auth/refresh} when the access token expires
 * @param expiresIn    seconds until the access token expires, so the client knows when to refresh
 * @param user         the logged-in user
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserDto user
) {
}
