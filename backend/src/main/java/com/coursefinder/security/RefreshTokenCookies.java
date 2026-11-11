package com.coursefinder.security;

import com.coursefinder.config.properties.RefreshTokenProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the {@code Set-Cookie} header that carries the refresh token.
 * <p>
 * The refresh token lives in a cookie instead of the JSON body so that JavaScript never sees it.
 * The access token stays in JS memory and is sent in the Authorization header as before.
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";

    // Only the auth endpoints get the cookie. Search, /api/me, etc. never receive it.
    private static final String PATH = "/api/auth";

    private final RefreshTokenProperties properties;

    public RefreshTokenCookies(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    /** @param lifetime how long the token is valid; usually 7 days, less near the end of a session */
    public ResponseCookie create(String rawToken, Duration lifetime) {
        return build(rawToken, lifetime);
    }

    /** Same cookie with an empty value and Max-Age=0, which tells the browser to delete it. */
    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                // HttpOnly: JavaScript can't read it (document.cookie doesn't show it), so an XSS bug
                // can't steal the refresh token. The browser still sends it automatically.
                .httpOnly(true)
                // Secure: only sent over HTTPS, so nobody on the network can sniff it.
                // Off for local development, because localhost runs on plain http.
                .secure(properties.cookieSecure())
                // SameSite=Lax: the browser doesn't attach it to requests started by other sites
                // (fetch, forms, images), only to top-level navigations. Our endpoints are POST,
                // so another site can't make the browser send this cookie to them. (Main CSRF defence.)
                .sameSite("Lax")
                // Path: only sent to /api/auth/..., not with every API call.
                .path(PATH)
                // Max-Age: the browser deletes it after this long. The real expiry is still checked
                // in the database; this just stops the browser from keeping a useless cookie.
                .maxAge(maxAge)
                .build();
    }
}
