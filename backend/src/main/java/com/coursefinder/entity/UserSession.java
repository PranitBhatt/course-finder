package com.coursefinder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One login on one device. All refresh tokens created by rotating from that login belong to it,
 * so revoking the session kills the whole chain at once.
 */
@Entity
@Table(name = "user_session")
public class UserSession {

    // Hibernate creates a random UUID when the session is first saved.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 32)
    private SessionRevokeReason revokeReason;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected UserSession() {
        // for JPA
    }

    public UserSession(Long userId, Instant now, Instant absoluteExpiresAt, String userAgent, String ipAddress) {
        this.userId = userId;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    /** Usable = not revoked and not past its absolute lifetime. */
    public boolean isActive(Instant now) {
        return revokedAt == null && absoluteExpiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(SessionRevokeReason reason, Instant now) {
        this.revokedAt = now;
        this.revokeReason = reason;
    }

    public void markUsed(Instant now) {
        this.lastUsedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public SessionRevokeReason getRevokeReason() {
        return revokeReason;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
