package com.coursefinder.controller;

import com.coursefinder.dto.response.UserDto;
import com.coursefinder.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * {@code GET /api/me}. The user id comes from the token (put there by JwtAuthenticationFilter),
     * never from the request, so nobody can ask for someone else's data.
     */
    @GetMapping
    public UserDto me(@AuthenticationPrincipal Long userId) {
        return userService.getById(userId);
    }
}
