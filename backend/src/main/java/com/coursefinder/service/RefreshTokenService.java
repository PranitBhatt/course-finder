package com.coursefinder.service;

import com.coursefinder.config.properties.RefreshTokenProperties;
import com.coursefinder.entity.RefreshToken;
import com.coursefinder.exception.InvalidRefreshTokenException;
import com.coursefinder.repository.RefreshTokenRepository;
import com.coursefinder.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Refresh tokens. The raw token only exists in the login/register response and on the client;
 * everything here hashes it first and works with the hash.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration expiration;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expiration = properties.expiration();
    }

    /** Creates a new refresh token for the user, stores its hash, and returns the raw token. */
    @Transactional
    public String create(Long userId) {
        String rawToken = TokenHasher.generateRawToken();
        refreshTokenRepository.save(
                new RefreshToken(userId, TokenHasher.hash(rawToken), Instant.now().plus(expiration)));
        // The only place the raw token leaves this class: straight into the response.
        return rawToken;
    }

    /** Returns the user id the token belongs to, or throws if it is unknown or expired. */
    @Transactional(readOnly = true)
    public Long findUserId(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshToken.isExpired()) {
            throw new InvalidRefreshTokenException();
        }
        return refreshToken.getUserId();
    }

    /** Deletes the token if it exists. Doing nothing when it doesn't is on purpose (see logout). */
    @Transactional
    public void delete(String rawToken) {
        refreshTokenRepository.deleteByTokenHash(TokenHasher.hash(rawToken));
    }

    /** Removes every expired token. Returns how many were deleted. */
    @Transactional
    public int deleteExpired() {
        return refreshTokenRepository.deleteExpired(Instant.now());
    }
}
