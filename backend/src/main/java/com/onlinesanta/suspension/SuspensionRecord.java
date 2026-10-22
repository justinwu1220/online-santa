package com.onlinesanta.suspension;

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
 * 停權當下保全的原始資訊（V37）。快照是證據：只增不改，解除停權只填 liftedAt。
 * 機構在停權期間修改資料，修改寫在 organization_revisions，不會動到這裡的快照。
 */
@Entity
@Table(name = "suspension_records")
public class SuspensionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private SuspensionTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String reason;

    /** 停權當下的資訊（JSON 文字）。 */
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String snapshot;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "lifted_by")
    private UUID liftedBy;

    protected SuspensionRecord() {
        // JPA
    }

    public SuspensionRecord(SuspensionTargetType targetType, UUID targetId, String reason, String snapshot,
                            UUID createdBy) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.snapshot = snapshot;
        this.createdBy = createdBy;
    }

    public void lift(UUID by) {
        if (liftedAt == null) {
            this.liftedAt = Instant.now();
            this.liftedBy = by;
        }
    }

    public Long getId() {
        return id;
    }

    public String getReason() {
        return reason;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLiftedAt() {
        return liftedAt;
    }
}
