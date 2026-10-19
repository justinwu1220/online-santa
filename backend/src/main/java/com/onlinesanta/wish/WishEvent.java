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

/**
 * 願望歷程的稽核軌跡，只增不改（比照 {@code ClaimEvent}：bigint 序號主鍵、沒有 updated_at）。
 */
@Entity
@Table(name = "wish_events")
public class WishEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wish_id", nullable = false, updatable = false)
    private UUID wishId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private WishEventType eventType;

    /** 系統動作為 null。 */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(columnDefinition = "text", updatable = false)
    private String note;

    /** 事件當下的願望內容（{@link WishSnapshot} 的 JSON）；不是每種事件都有。 */
    @Column(columnDefinition = "text", updatable = false)
    private String snapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WishEvent() {
        // JPA
    }

    private WishEvent(UUID wishId, WishEventType eventType, UUID actorUserId, String note, String snapshot) {
        this.wishId = wishId;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.note = note;
        this.snapshot = snapshot;
        this.createdAt = Instant.now();
    }

    public static WishEvent by(UUID wishId, WishEventType type, UUID actorUserId, String note, String snapshot) {
        return new WishEvent(wishId, type, actorUserId, note, snapshot);
    }

    public Long getId() {
        return id;
    }

    public UUID getWishId() {
        return wishId;
    }

    public WishEventType getEventType() {
        return eventType;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getNote() {
        return note;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
