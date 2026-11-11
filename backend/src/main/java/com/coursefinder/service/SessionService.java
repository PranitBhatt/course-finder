package com.coursefinder.service;

import com.coursefinder.config.properties.RefreshTokenProperties;
import com.coursefinder.config.properties.SessionProperties;
import com.coursefinder.entity.RefreshToken;
import com.coursefinder.entity.SessionRevokeReason;
import com.coursefinder.entity.UserSession;
import com.coursefinder.exception.InvalidRefreshTokenException;
import com.coursefinder.repository.RefreshTokenRepository;
import com.coursefinder.repository.UserSessionRepository;
import com.coursefinder.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Login sessions and their rotating refresh tokens.
 * <p>
 * Rotation: every refresh token works exactly once. Using it returns a new one and marks the old
 * one as used. The real client always holds the newest token, so if a USED token shows up again,
 * someone else has a copy. We can't tell which side is the thief, so the whole session is revoked
 * and both have to log in again - the real user can, the thief can't.
 * <p>
 * The raw tokens only exist in the response and on the client; this class works with hashes.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenLifetime;
    private final Duration reuseGracePeriod;
    private final Duration sessionAbsoluteLifetime;

    public SessionService(UserSessionRepository sessionRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          RefreshTokenProperties refreshTokenProperties,
                          SessionProperties sessionProperties) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenLifetime = refreshTokenProperties.expiration();
        this.reuseGracePeriod = refreshTokenProperties.reuseGracePeriod();
        this.sessionAbsoluteLifetime = sessionProperties.absoluteLifetime();
    }

    /**
     * What login and refresh hand back to the controller.
     *
     * @param refreshToken         raw token for the cookie
     * @param refreshTokenLifetime how long it is valid, for the cookie's Max-Age
     */
    public record IssuedTokens(Long userId, UUID sessionId, String refreshToken, Duration refreshTokenLifetime) {
    }

    /** Login/register: starts a new session and creates its first refresh token. */
    @Transactional
    public IssuedTokens startSession(Long userId, String userAgent, String ipAddress) {
        Instant now = Instant.now();
        UserSession session = sessionRepository.save(new UserSession(
                userId, now, now.plus(sessionAbsoluteLifetime), truncate(userAgent, 512), ipAddress));
        return issueRefreshToken(session, now);
    }

    /**
     * Exchanges a refresh token for a new one in the same session. Throws (401) if the token
     * is unknown, expired, already used, or its session is revoked or past its absolute lifetime.
     * <p>
     * noRollbackFor: normally an exception undoes everything the transaction changed. On reuse we
     * revoke the session and THEN throw - without this, the revoke would be rolled back and the
     * stolen token's session would stay alive.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedTokens refresh(String rawToken) {
        Instant now = Instant.now();

        // Locks the token row until this transaction ends, so two refreshes with the same token
        // can't both get past the "already used?" check below. See findByTokenHashForUpdate.
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(TokenHasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        UserSession session = sessionRepository.findById(token.getSessionId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!session.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }

        if (token.isUsed()) {
            // Grace window: a used token coming back within a few seconds is almost always our own
            // frontend - two tabs, or a retried request, that sent the same cookie at the same time.
            // The other request already got the new token, so this one just fails (401) and the
            // session stays alive.
            //
            // The trade-off: if a thief uses a stolen token first and the real user's copy arrives
            // within the window, that reuse is not detected - the real user gets a 401 and the thief
            // keeps the session until the next reuse is caught. A short window keeps that gap small;
            // a reuse after the window is treated as theft, exactly as before.
            if (token.getUsedAt().plus(reuseGracePeriod).isAfter(now)) {
                log.info("Refresh token reused within the grace period - session {} not revoked", session.getId());
                throw new InvalidRefreshTokenException();
            }

            session.revoke(SessionRevokeReason.REUSE_DETECTED, now);
            // Ids only: logging the token would hand it to anyone who can read the logs.
            log.warn("Refresh token reuse detected - revoked session {} of user {}",
                    session.getId(), session.getUserId());
            throw new InvalidRefreshTokenException();
        }

        // The entities are managed by JPA, so these changes are saved when the transaction commits.
        token.markUsed(now);
        session.markUsed(now);
        return issueRefreshToken(session, now);
    }

    /**
     * Logout: revokes the session the token belongs to. Any token of the session identifies it,
     * even an old or expired one. Unknown tokens or already revoked sessions are ignored (idempotent).
     */
    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .flatMap(token -> sessionRepository.findById(token.getSessionId()))
                .filter(session -> !session.isRevoked())
                .ifPresent(session -> session.revoke(SessionRevokeReason.LOGOUT, Instant.now()));
    }

    /** Removes every expired refresh token. Returns how many were deleted. */
    @Transactional
    public int deleteExpiredRefreshTokens() {
        return refreshTokenRepository.deleteExpired(Instant.now());
    }

    private IssuedTokens issueRefreshToken(UserSession session, Instant now) {
        // Never valid longer than the session itself: near the end of the 30 days the
        // last tokens get shorter, so refreshing can't stretch the session.
        Instant expiresAt = now.plus(refreshTokenLifetime);
        if (expiresAt.isAfter(session.getAbsoluteExpiresAt())) {
            expiresAt = session.getAbsoluteExpiresAt();
        }

        String rawToken = TokenHasher.generateRawToken();
        refreshTokenRepository.save(new RefreshToken(session.getId(), TokenHasher.hash(rawToken), now, expiresAt));

        return new IssuedTokens(session.getUserId(), session.getId(), rawToken, Duration.between(now, expiresAt));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
