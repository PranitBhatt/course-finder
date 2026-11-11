package com.coursefinder.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Password rules on RegisterRequest, and that request objects never print secrets. */
class AuthRequestsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> passwordErrors(String password) {
        Set<ConstraintViolation<RegisterRequest>> violations =
                validator.validate(new RegisterRequest("ada@example.com", password, "Ada"));
        return violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("password"))
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void passwordNeedsAtLeast8Characters() {
        assertThat(passwordErrors("1234567")).containsExactly("must be at least 8 characters");
        assertThat(passwordErrors("12345678")).isEmpty();
    }

    @Test
    void passwordLimitIs72BytesNotCharacters() {
        assertThat(passwordErrors("a".repeat(72))).isEmpty();
        assertThat(passwordErrors("a".repeat(73))).hasSize(1);

        // "é" is 1 character but 2 bytes: 36 of them = 72 bytes (ok), 37 = 74 bytes (too long).
        assertThat("é".repeat(37).getBytes(StandardCharsets.UTF_8)).hasSize(74);
        assertThat(passwordErrors("é".repeat(36))).isEmpty();
        assertThat(passwordErrors("é".repeat(37))).hasSize(1);

        // An emoji is 4 bytes: 18 = 72 bytes (ok), 19 = 76 bytes (too long).
        assertThat(passwordErrors("😀".repeat(18))).isEmpty();
        assertThat(passwordErrors("😀".repeat(19))).hasSize(1);
    }

    @Test
    void toStringNeverContainsThePasswordOrFullEmail() {
        String login = new LoginRequest("ada@example.com", "s3cret-pass").toString();
        String register = new RegisterRequest("ada@example.com", "s3cret-pass", "Ada").toString();

        assertThat(login).doesNotContain("s3cret-pass").doesNotContain("ada@example.com").contains("a***@example.com");
        assertThat(register).doesNotContain("s3cret-pass").doesNotContain("ada@example.com").contains("a***@example.com");
    }
}
