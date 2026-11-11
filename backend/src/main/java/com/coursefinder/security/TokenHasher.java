package com.coursefinder.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Creates random refresh tokens and hashes them for storage.
 * <p>
 * The database only ever sees the hash. If the table leaks, the hashes are useless:
 * the API wants the raw token, and you can't get it back from the hash.
 */
public final class TokenHasher {

    // SecureRandom, not Random: Random is predictable if you've seen a few outputs,
    // so an attacker could guess other people's tokens. One shared instance is thread-safe.
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHasher() {
    }

    /** 32 random bytes (256 bits), URL-safe Base64 without padding = 43 characters. */
    public static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 as 64 hex characters.
     * <p>
     * A fast hash is fine here, unlike for passwords: passwords are short and guessable, so they
     * need a slow hash like BCrypt. These tokens are 256 random bits, which nobody can brute-force
     * however fast the hash is. It also has no salt on purpose: the same token must always give
     * the same hash, so we can find the row with {@code WHERE token_hash = ?}.
     */
    public static String hash(String rawToken) {
        try {
            // MessageDigest is not thread-safe, so get a new one per call (it's cheap).
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to support SHA-256, so this can't really happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
