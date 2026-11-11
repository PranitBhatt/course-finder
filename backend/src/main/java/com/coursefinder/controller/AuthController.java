package com.coursefinder.controller;

import com.coursefinder.dto.request.LoginRequest;
import com.coursefinder.dto.request.RegisterRequest;
import com.coursefinder.dto.response.UserDto;
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
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "app.features.auth", name = "enabled", havingValue = "true")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** {@code POST /api/auth/register} - creates an account, 409 if the email is taken. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    /** {@code POST /api/auth/login} - 200 with the user if the email and password match, otherwise 401. */
    @PostMapping("/login")
    public UserDto login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }
}
