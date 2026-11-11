package com.coursefinder.service;

import com.coursefinder.config.properties.LoginProperties;
import com.coursefinder.dto.request.LoginRequest;
import com.coursefinder.entity.AppUser;
import com.coursefinder.entity.UserStatus;
import com.coursefinder.exception.AccountDisabledException;
import com.coursefinder.exception.AccountLockedException;
import com.coursefinder.exception.InvalidCredentialsException;
import com.coursefinder.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The login rules on their own: lockout, reset, timing, disabled users, hash upgrades.
 * Uses a real BCrypt encoder with a low cost (fast tests) and a mocked repository.
 */
class UserServiceLoginTest {

    private static final String EMAIL = "ada@example.com";
    private static final String PASSWORD = "correct-password";
    private static final Duration LOCK = Duration.ofMinutes(15);

    // Cost 5 for the "current" encoder; a user hashed with cost 4 counts as outdated.
    private final BCryptPasswordEncoder encoder = spy(new BCryptPasswordEncoder(5));
    private final AppUserRepository repository = mock(AppUserRepository.class);

    private UserService service(Duration lockDuration) {
        return new UserService(repository, encoder, new LoginProperties(5, lockDuration));
    }

    private AppUser existingUser(String hash) {
        AppUser user = new AppUser(EMAIL, hash, "Ada");
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    private static LoginRequest login(String password) {
        return new LoginRequest(EMAIL, password);
    }

    @Test
    void unknownEmailStillRunsBcryptAgainstTheDummyHash() {
        UserService service = service(LOCK);
        when(repository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "guess-1234")))
                .isInstanceOf(InvalidCredentialsException.class);

        // The same slow BCrypt work as a real wrong password, so timing gives nothing away.
        verify(encoder).matches(eq("guess-1234"), startsWith("$2a$05$"));
    }

    @Test
    void fifthWrongPasswordLocksTheAccountWith429() {
        UserService service = service(LOCK);
        AppUser user = existingUser(encoder.encode(PASSWORD));

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> service.login(login("wrong"))).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(user.getFailedLoginCount()).isEqualTo(4);

        assertThatThrownBy(() -> service.login(login("wrong")))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(e -> assertThat(((AccountLockedException) e).getRetryAfter()).isEqualTo(LOCK));
        assertThat(user.isLocked(Instant.now())).isTrue();

        // While locked, even the correct password is refused - and not even checked.
        int bcryptCallsBefore = countMatchesCalls();
        assertThatThrownBy(() -> service.login(login(PASSWORD))).isInstanceOf(AccountLockedException.class);
        assertThat(countMatchesCalls()).isEqualTo(bcryptCallsBefore);
    }

    @Test
    void lockIsTemporaryAndSuccessResetsEverything() throws Exception {
        UserService service = service(Duration.ofMillis(50));
        AppUser user = existingUser(encoder.encode(PASSWORD));

        for (int i = 1; i <= 5; i++) {
            try {
                service.login(login("wrong"));
            } catch (InvalidCredentialsException | AccountLockedException expected) {
                // the 5th one locks
            }
        }
        assertThat(user.getLockedUntil()).isNotNull();

        Thread.sleep(100); // the (very short) lock runs out

        service.login(login(PASSWORD));
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        UserService service = service(LOCK);
        AppUser user = existingUser(encoder.encode(PASSWORD));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(login("wrong"))).isInstanceOf(InvalidCredentialsException.class);
        }
        service.login(login(PASSWORD));
        assertThat(user.getFailedLoginCount()).isZero();

        // 4 more mistakes don't lock: the earlier 3 were wiped out by the success.
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.login(login("wrong"))).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(user.isLocked(Instant.now())).isFalse();
    }

    @Test
    void disabledUserGets403OnlyWithTheCorrectPassword() {
        UserService service = service(LOCK);
        AppUser user = existingUser(encoder.encode(PASSWORD));
        ReflectionTestUtils.setField(user, "status", UserStatus.DISABLED);

        assertThatThrownBy(() -> service.login(login("wrong"))).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login(login(PASSWORD))).isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void outdatedHashIsUpgradedOnLogin() {
        UserService service = service(LOCK);
        AppUser user = existingUser(new BCryptPasswordEncoder(4).encode(PASSWORD));
        assertThat(user.getPasswordHash()).startsWith("$2a$04$");

        service.login(login(PASSWORD));

        assertThat(user.getPasswordHash()).startsWith("$2a$05$");
        assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void currentHashIsNotRehashed() {
        UserService service = service(LOCK);
        String hash = encoder.encode(PASSWORD);
        AppUser user = existingUser(hash);

        service.login(login(PASSWORD));

        assertThat(user.getPasswordHash()).isEqualTo(hash);
    }

    private int countMatchesCalls() {
        return (int) org.mockito.Mockito.mockingDetails(encoder).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("matches"))
                .count();
    }
}
