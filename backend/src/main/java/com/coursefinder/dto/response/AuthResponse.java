package com.coursefinder.dto.response;

/**
 * Returned by register and login. The refresh token is not in here: it is set as an
 * HttpOnly cookie, so JavaScript never sees it.
 *
 * @param accessToken short-lived JWT to send as {@code Authorization: Bearer <token>}
 * @param expiresIn   seconds until the access token expires, so the client knows when to refresh
 * @param user        the logged-in user
 */
public record AuthResponse(
        String accessToken,
        long expiresIn,
        UserDto user
) {
}
