package com.onlinesanta.auth;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 一次性的信箱驗證碼或重設密碼 token。資料庫只存雜湊，不存明文（見 V44 的說明）。
 */
@Entity
@Table(name = "auth_tokens")
public class AuthToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AuthTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 128)
    private String tokenHash;

    /** 驗證碼被猜錯的次數；達到上限即作廢。 */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected AuthToken() {
        // JPA
    }

    public AuthToken(UUID userId, AuthTokenPurpose purpose, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public AuthTokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void recordFailedAttempt() {
        this.attempts++;
    }

    public void markUsed(Instant at) {
        if (usedAt == null) {
            this.usedAt = at;
        }
    }
}
