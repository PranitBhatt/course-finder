package com.coursefinder.security;

import com.coursefinder.config.properties.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and checks JWT access tokens.
 * <p>
 * Only exists when auth is enabled, so prod doesn't need JWT_SECRET until auth is switched on there.
 */
@Service
@ConditionalOnProperty(prefix = "app.features.auth", name = "enabled", havingValue = "true")
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        // hmacShaKeyFor refuses keys shorter than 256 bits (32 bytes), which HS256 requires.
        // That's on purpose: with auth enabled and JWT_SECRET missing or short, the app fails
        // at startup instead of signing tokens with a guessable key.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.accessTokenExpiration();
    }

    public String createToken(Long userId, UUID sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                // "sub" (subject) = who the token is about. The id never changes, unlike the email.
                .subject(String.valueOf(userId))
                // "sid" = which login session issued it, so a request can be tied to a session.
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** How long a new access token is valid; sent to the client as "expiresIn". */
    public Duration getExpiration() {
        return expiration;
    }

    /**
     * Returns the user id from a valid token.
     * Throws {@link JwtException} if the signature doesn't match (token was changed),
     * it has expired, or it isn't a JWT at all.
     */
    public Long parseUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }
}
