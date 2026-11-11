package com.coursefinder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    // Declared as the PasswordEncoder interface so the rest of the code doesn't care which
    // algorithm is used. BCrypt is slow on purpose and adds a random salt to every hash,
    // so leaked hashes are expensive to crack and two equal passwords never look the same.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
