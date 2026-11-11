package com.coursefinder.dto.response;

import com.coursefinder.entity.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns about a user. There is deliberately NO password field:
 * returning the entity directly would send the password back over the wire.
 *
 * @param id the user's PUBLIC id (a random UUID), never the internal numeric database id
 */
public record UserDto(
        UUID id,
        String email,
        String displayName,
        Role role,
        Instant createdAt
) {
}
