package com.coursefinder.repository;

import com.coursefinder.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Same lookup, but locks the row ({@code SELECT ... FOR UPDATE}) until the transaction ends.
     * <p>
     * Without the lock, two refreshes with the same token at the same moment both read
     * {@code used_at = null}, both think they are first, and both create a new token - the session
     * ends up with two valid chains. With the lock, the second request waits at this line until the
     * first one commits, and then reads the row again with {@code used_at} already set.
     * <p>
     * Only works inside a transaction (the lock is released at commit or rollback).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // One DELETE statement instead of loading every expired row first (which a derived
    // deleteBy... would do). Returns how many rows were removed.
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
