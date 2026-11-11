package com.coursefinder.service;

import com.coursefinder.config.properties.LoginProperties;
import com.coursefinder.dto.request.LoginRequest;
import com.coursefinder.dto.request.RegisterRequest;
import com.coursefinder.dto.response.UserDto;
import com.coursefinder.entity.AppUser;
import com.coursefinder.exception.AccountDisabledException;
import com.coursefinder.exception.AccountLockedException;
import com.coursefinder.exception.EmailAlreadyRegisteredException;
import com.coursefinder.exception.InvalidCredentialsException;
import com.coursefinder.exception.ResourceNotFoundException;
import com.coursefinder.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginProperties loginProperties;

    // A real BCrypt hash of a password nobody has, made with the same encoder (so the same cost)
    // as real hashes. Used to make "unknown email" take as long as "wrong password".
    private final String dummyPasswordHash;

    public UserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                       LoginProperties loginProperties) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginProperties = loginProperties;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Result of register/login: the public view of the user plus the internal id, which the
     * session needs (sessions reference users by their numeric id inside the database).
     */
    public record AuthenticatedUser(Long internalId, UserDto user) {
    }

    @Transactional
    public AuthenticatedUser register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        // Quick check so the common case gets a clear 409 without touching the insert.
        if (appUserRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), request.displayName());
        // Registering logs the user in right away, so this counts as their first login.
        user.recordSuccessfulLogin(Instant.now());

        // The check above is not enough on its own: two requests with the same email can both
        // run existsByEmail() before either has inserted, both see "false", and both try to insert.
        // Only the UNIQUE constraint in the database can stop the second one, so we catch that
        // error here. saveAndFlush sends the INSERT now, so the error is thrown inside this try.
        try {
            AppUser saved = appUserRepository.saveAndFlush(user);
            return new AuthenticatedUser(saved.getId(), toDto(saved));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    /**
     * Checks email + password. Order matters:
     * <ol>
     *   <li>unknown email: still run BCrypt once, then 401</li>
     *   <li>locked account: 429, without even checking the password (guesses during a lock are useless)</li>
     *   <li>wrong password: count it, maybe lock, then 401 (or 429 if this one caused the lock)</li>
     *   <li>disabled account: 403 - only now, so only someone with the right password learns it</li>
     *   <li>success: reset the counter, record the login, upgrade the hash if needed</li>
     * </ol>
     * noRollbackFor: the failed-login counter is changed and THEN an exception is thrown. Without this,
     * the exception would roll the counter back and an attacker could guess forever.
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public AuthenticatedUser login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Instant now = Instant.now();

        Optional<AppUser> found = appUserRepository.findByEmail(email);
        if (found.isEmpty()) {
            // Without this, "no such email" returns in ~1 ms and "wrong password" in ~100 ms (BCrypt),
            // so an attacker could time the responses to find out which emails have an account.
            // Comparing against a dummy hash makes both cases do the same slow work.
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        AppUser user = found.get();

        if (user.isLocked(now)) {
            throw new AccountLockedException(Duration.between(now, user.getLockedUntil()));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean lockedNow = user.recordFailedLogin(
                    now, loginProperties.maxFailedAttempts(), loginProperties.lockDuration());
            if (lockedNow) {
                // Public id only - never the email, never what was typed.
                log.warn("Account {} locked for {} after {} failed logins",
                        user.getPublicId(), loginProperties.lockDuration(), loginProperties.maxFailedAttempts());
                throw new AccountLockedException(loginProperties.lockDuration());
            }
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        // upgradeEncoding is true when the stored hash is weaker than what the encoder makes today
        // (e.g. created with a lower BCrypt cost). This is the only moment we have the plain password,
        // so it's the only moment we can re-hash it. Users get the stronger hash just by logging in.
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(request.password()));
        }

        // The entity is managed by JPA, so these changes are saved when the transaction commits.
        user.recordSuccessfulLogin(now);
        return new AuthenticatedUser(user.getId(), toDto(user));
    }

    /** By internal id - used inside the backend (e.g. after a refresh, which knows the session's user). */
    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        return appUserRepository.findById(id)
                .map(UserService::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /** By public id - used for anything that comes from outside (e.g. the JWT "sub"). */
    @Transactional(readOnly = true)
    public UserDto getByPublicId(UUID publicId) {
        return appUserRepository.findByPublicId(publicId)
                .map(UserService::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("User", publicId));
    }

    // Locale.ROOT instead of the server's default locale: in some locales (e.g. Turkish)
    // "I".toLowerCase() gives a dotless "ı", so the same email could be stored differently
    // depending on which machine the app runs on.
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Map by hand so the password hash and the internal id can never end up in a response.
    private static UserDto toDto(AppUser user) {
        return new UserDto(user.getPublicId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getCreatedAt());
    }
}
