package com.coursefinder.dto.response;

/**
 * Returned by register and login.
 *
 * @param token JWT to send back as {@code Authorization: Bearer <token>}
 * @param user  the logged-in user
 */
public record AuthResponse(
        String token,
        UserDto user
) {
}
