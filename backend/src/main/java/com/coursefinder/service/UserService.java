package com.coursefinder.service;

import com.coursefinder.dto.request.LoginRequest;
import com.coursefinder.dto.request.RegisterRequest;
import com.coursefinder.dto.response.UserDto;
import com.coursefinder.entity.AppUser;
import com.coursefinder.exception.EmailAlreadyRegisteredException;
import com.coursefinder.exception.InvalidCredentialsException;
import com.coursefinder.exception.ResourceNotFoundException;
import com.coursefinder.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        // Quick check so the common case gets a clear 409 without touching the insert.
        if (appUserRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), request.displayName());

        // The check above is not enough on its own: two requests with the same email can both
        // run existsByEmail() before either has inserted, both see "false", and both try to insert.
        // Only the UNIQUE constraint in the database can stop the second one, so we catch that
        // error here. saveAndFlush sends the INSERT now, so the error is thrown inside this try.
        try {
            AppUser saved = appUserRepository.saveAndFlush(user);
            return toDto(saved);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException(email);
        }
    }

    @Transactional(readOnly = true)
    public UserDto login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        // matches() hashes the typed password with the salt stored inside the hash and compares.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return toDto(user);
    }

    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        return appUserRepository.findById(id)
                .map(UserService::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // Locale.ROOT instead of the server's default locale: in some locales (e.g. Turkish)
    // "I".toLowerCase() gives a dotless "ı", so the same email could be stored differently
    // depending on which machine the app runs on.
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Map by hand so the password hash can never end up in a response.
    private static UserDto toDto(AppUser user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
