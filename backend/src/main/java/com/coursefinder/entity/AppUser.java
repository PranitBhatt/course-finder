package com.coursefinder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A registered user. Not called "User" in the database because that is a reserved word in PostgreSQL.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    // Internal only: foreign keys and joins. Never sent to clients (see publicId).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The id clients see. Random, so it gives nothing away and can't be guessed.
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, unique = true)
    private String email;

    // A BCrypt hash, never the real password. Login hashes what the user typed and compares.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {
        // for JPA
    }

    public AppUser(String email, String passwordHash, String displayName) {
        // Set here, not left to the database default: Hibernate would otherwise insert NULL.
        this.publicId = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        // Everyone starts as a normal user. ADMIN is only ever given by hand in the database.
        this.role = Role.USER;
        this.status = UserStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Counts a wrong password. On the {@code maxAttempts}-th failure the account is locked for
     * {@code lockDuration} and the counter starts over, so after the lock ends there are
     * {@code maxAttempts} new tries. Returns true if this failure caused the lock.
     */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    /** A correct password wipes out earlier failures, so typos spread over days never add up to a lock. */
    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
