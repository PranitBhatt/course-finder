package com.coursefinder.controller;

import com.coursefinder.dto.response.UserDto;
import com.coursefinder.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The logged-in user's own data. Needs a valid Bearer token (see SecurityConfig). */
@RestController
@RequestMapping("/api/me")
@ConditionalOnProperty(prefix = "app.features.auth", name = "enabled", havingValue = "true")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    /**
     * {@code GET /api/me}. Spring has already verified the token and hands us its claims.
     * The user id comes from "sub" in the signed token, never from the request,
     * so nobody can ask for someone else's data.
     */
    @GetMapping
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        // "sub" is the public id; the decoder already made sure it's a valid UUID.
        return userService.getByPublicId(UUID.fromString(jwt.getSubject()));
    }
}
