package com.coursefinder.dto.response;

import com.coursefinder.entity.Role;

import java.time.Instant;

/**
 * What the API returns about a user. There is deliberately NO password field:
 * returning the entity directly would send the password back over the wire.
 */
public record UserDto(
        Long id,
        String email,
        String displayName,
        Role role,
        Instant createdAt
) {
}
