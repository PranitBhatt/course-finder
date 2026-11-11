package com.coursefinder.service;

import com.coursefinder.dto.request.RegisterRequest;
import com.coursefinder.dto.response.UserDto;
import com.coursefinder.entity.AppUser;
import com.coursefinder.exception.EmailAlreadyRegisteredException;
import com.coursefinder.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;

    public UserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        // Check first so the caller gets a clear 409 instead of a database error.
        if (appUserRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        // TODO: hash the password. Right now it's saved as plain text, so a DB leak exposes every password.
        AppUser user = new AppUser(request.email(), request.password(), request.displayName());
        AppUser saved = appUserRepository.save(user);

        // Map to a DTO by hand so the password can never end up in the response.
        return new UserDto(saved.getId(), saved.getEmail(), saved.getDisplayName(), saved.getCreatedAt());
    }
}
