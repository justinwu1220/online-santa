package com.onlinesanta.unlock;

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
 * 機構對管理員提出的解除申請（願望凍結或機構停權）。同一個對象同時只能有一筆待審的申請（V36 的唯一索引）。
 * 決定後不再更動，申請內容與決定都留作紀錄。
 */
@Entity
@Table(name = "unlock_requests")
public class UnlockRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private UnlockTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnlockStatus status = UnlockStatus.PENDING;

    @Column(columnDefinition = "text", updatable = false)
    private String message;

    /** 機構修改後的內容（JSON 文字）；沒有修改就是 null。核准時才套用到線上內容。 */
    @Column(columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    protected UnlockRequest() {
        // JPA
    }

    public UnlockRequest(UnlockTargetType targetType, UUID targetId, String message, String payload, UUID requestedBy) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.message = message;
        this.payload = payload;
        this.requestedBy = requestedBy;
    }

    public void decide(UnlockStatus decision, UUID by, String note) {
        this.status = decision;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
    }

    public Long getId() {
        return id;
    }

    public UnlockTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public UnlockStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }
}
