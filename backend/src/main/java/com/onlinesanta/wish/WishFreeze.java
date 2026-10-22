package com.onlinesanta.wish;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 願望的一筆凍結紀錄（append-only，解除只填 liftedAt）。見 V35。 */
@Entity
@Table(name = "wish_freezes")
public class WishFreeze {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wish_id", nullable = false)
    private UUID wishId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WishFreezeCause cause;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "lifted_by")
    private UUID liftedBy;

    protected WishFreeze() {
        // JPA
    }

    WishFreeze(UUID wishId, WishFreezeCause cause, String reason, UUID createdBy) {
        this.wishId = wishId;
        this.cause = cause;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    void lift(UUID by) {
        this.liftedAt = Instant.now();
        this.liftedBy = by;
    }

    public UUID getWishId() {
        return wishId;
    }

    public WishFreezeCause getCause() {
        return cause;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public boolean isActive() {
        return liftedAt == null;
    }
}
