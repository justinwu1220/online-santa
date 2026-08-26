package com.onlinesanta.claim;

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
 * 認領歷程的稽核軌跡，只增不改。
 *
 * <p>不繼承 {@code BaseEntity}：這張表用 bigint 序號主鍵（寫入頻繁且天然依時間排序），
 * 也不需要 updated_at——一筆事件寫下去就不該再變動。
 */
@Entity
@Table(name = "claim_events")
public class ClaimEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private ClaimEventType eventType;

    /** 由排程觸發時為 null，代表系統自身的動作。 */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(length = 500, updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClaimEvent() {
        // JPA
    }

    private ClaimEvent(UUID claimId, ClaimEventType eventType, UUID actorUserId, String note) {
        this.claimId = claimId;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public static ClaimEvent by(UUID claimId, ClaimEventType type, UUID actorUserId, String note) {
        return new ClaimEvent(claimId, type, actorUserId, note);
    }

    /** 系統自動產生的事件（排程釋回等）。 */
    public static ClaimEvent bySystem(UUID claimId, ClaimEventType type, String note) {
        return new ClaimEvent(claimId, type, null, note);
    }

    public Long getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public ClaimEventType getEventType() {
        return eventType;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
