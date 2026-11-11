package com.coursefinder.controller;

import com.coursefinder.dto.request.LoginRequest;
import com.coursefinder.dto.request.RefreshTokenRequest;
import com.coursefinder.dto.request.RegisterRequest;
import com.coursefinder.dto.response.AccessTokenResponse;
import com.coursefinder.dto.response.AuthResponse;
import com.coursefinder.dto.response.UserDto;
import com.coursefinder.security.JwtService;
import com.coursefinder.service.RefreshTokenService;
import com.coursefinder.service.UserService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
 * what you call when the access token has expired.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "app.features.auth", name = "enabled", havingValue = "true")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserService userService, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /** {@code POST /api/auth/register} - creates an account and logs it in, 409 if the email is taken. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        UserDto user = userService.register(request);
        // Return tokens right away so the user doesn't have to log in again after signing up.
        return issueTokens(user);
    }

    /** {@code POST /api/auth/login} - 200 with tokens if the email and password match, otherwise 401. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        UserDto user = userService.login(request);
        return issueTokens(user);
    }

    /** {@code POST /api/auth/refresh} - new access token for a valid refresh token, otherwise 401. */
    @PostMapping("/refresh")
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        Long userId = refreshTokenService.findUserId(request.refreshToken());
        return new AccessTokenResponse(jwtService.createToken(userId), jwtService.getExpiration().toSeconds());
    }

    /**
     * {@code POST /api/auth/logout} - deletes the refresh token. Always 204.
     * <p>
     * Idempotent: calling it once or five times leaves the same result (the token is gone).
     * A retry after a network error, a double click, or two open tabs all logging out
     * shouldn't show an error, because the user got exactly what they asked for.
     * It also doesn't reveal whether a token existed.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.delete(request.refreshToken());
    }

    private AuthResponse issueTokens(UserDto user) {
        String accessToken = jwtService.createToken(user.id());
        String refreshToken = refreshTokenService.create(user.id());
        return new AuthResponse(accessToken, refreshToken, jwtService.getExpiration().toSeconds(), user);
    }
}
