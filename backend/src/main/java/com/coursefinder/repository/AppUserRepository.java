package com.coursefinder.repository;

import com.coursefinder.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Spring Data builds the SQL from the method name: SELECT ... WHERE email = ?
    boolean existsByEmail(String email);

    Optional<AppUser> findByEmail(String email);
}
