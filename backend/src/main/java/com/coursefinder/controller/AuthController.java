package com.coursefinder.controller;

import com.coursefinder.dto.request.LoginRequest;
import com.coursefinder.dto.request.RegisterRequest;
import com.coursefinder.dto.response.AccessTokenResponse;
import com.coursefinder.dto.response.AuthResponse;
import com.coursefinder.dto.response.UserDto;
import com.coursefinder.exception.InvalidRefreshTokenException;
import com.coursefinder.security.JwtService;
import com.coursefinder.security.RefreshTokenCookies;
import com.coursefinder.service.SessionService;
import com.coursefinder.service.SessionService.IssuedTokens;
import com.coursefinder.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints, behind the feature flag {@code app.features.auth.enabled}.
 * <p>
 * Why the flag lives on the server and not only in the UI: hiding a "Sign up" button
 * doesn't stop anyone from sending {@code curl -X POST /api/auth/register} directly.
 * With {@code @ConditionalOnProperty}, when the flag is false this controller is never
 * created, so its routes don't exist and every request gets a plain 404.
 * <p>
 * None of these need an access token (see SecurityConfig): refresh and logout are exactly
 * what you call when the access token has expired. They use the refresh token cookie instead,
 * which OriginCheckFilter protects against CSRF.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "app.features.auth", name = "enabled", havingValue = "true")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final RefreshTokenCookies refreshTokenCookies;

    public AuthController(UserService userService, JwtService jwtService,
                          SessionService sessionService, RefreshTokenCookies refreshTokenCookies) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.refreshTokenCookies = refreshTokenCookies;
    }

    /** {@code POST /api/auth/register} - creates an account and logs it in, 409 if the email is taken. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        UserDto user = userService.register(request);
        // Log the user in right away so they don't have to log in again after signing up.
        return startSession(HttpStatus.CREATED, user, httpRequest);
    }

    /** {@code POST /api/auth/login} - 200 with tokens if the email and password match, otherwise 401. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        UserDto user = userService.login(request);
        return startSession(HttpStatus.OK, user, httpRequest);
    }

    /**
     * {@code POST /api/auth/refresh} - trades the refresh token cookie for a new access token AND
     * a new refresh token (rotation). The old refresh token can't be used again. 401 if invalid.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        IssuedTokens tokens = sessionService.refresh(refreshToken);

        AccessTokenResponse body = new AccessTokenResponse(
                jwtService.createToken(tokens.userId(), tokens.sessionId()), jwtService.getExpiration().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens))
                .body(body);
    }

    /**
     * {@code POST /api/auth/logout} - ends the session and deletes the cookie. Always 204.
     * <p>
     * Idempotent: calling it once or five times leaves the same result (the session is over).
     * A retry after a network error, a double click, or two open tabs all logging out
     * shouldn't show an error, because the user got exactly what they asked for.
     * It also doesn't reveal whether a token existed.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessionService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> startSession(HttpStatus status, UserDto user, HttpServletRequest httpRequest) {
        // Stored with the session so the user can later see where they're logged in.
        // getRemoteAddr() is the real client IP on Render thanks to server.forward-headers-strategy.
        IssuedTokens tokens = sessionService.startSession(
                user.id(), httpRequest.getHeader(HttpHeaders.USER_AGENT), httpRequest.getRemoteAddr());

        AuthResponse body = new AuthResponse(
                jwtService.createToken(user.id(), tokens.sessionId()), jwtService.getExpiration().toSeconds(), user);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens))
                .body(body);
    }

    private String refreshCookie(IssuedTokens tokens) {
        return refreshTokenCookies.create(tokens.refreshToken(), tokens.refreshTokenLifetime()).toString();
    }
}
