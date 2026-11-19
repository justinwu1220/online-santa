package com.onlinesanta.closing;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** 一次結案文件匯出作業（進度存資料庫，可由多次請求接續推進）。 */
@Entity
@Table(name = "closing_exports")
public class ClosingExport extends BaseEntity {

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ExportKind kind;

    /** 結案文件包的階段（C／D／CD）；收據匯出沒有。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 4)
    private com.onlinesanta.closing.doc.ClosingStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExportStatus status;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int prepared;

    @Column(name = "object_name", length = 255)
    private String objectName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false)
    private int skipped;

    @Column(length = 500)
    private String error;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected ClosingExport() {
    }

    public ClosingExport(UUID campaignId, ExportKind kind, int total, UUID createdBy) {
        this.campaignId = campaignId;
        this.kind = kind;
        this.status = ExportStatus.PREPARING;
        this.total = total;
        this.createdBy = createdBy;
    }

    public ClosingExport(UUID campaignId, ExportKind kind, com.onlinesanta.closing.doc.ClosingStage stage, int total,
                         UUID createdBy) {
        this(campaignId, kind, total, createdBy);
        this.stage = stage;
    }

    public void progress(int prepared) {
        this.prepared = prepared;
    }

    public void ready(String objectName, long sizeBytes, int skipped, Instant expiresAt) {
        this.status = ExportStatus.READY;
        this.objectName = objectName;
        this.sizeBytes = sizeBytes;
        this.skipped = skipped;
        this.expiresAt = expiresAt;
        this.prepared = total;
        this.error = null;
    }

    public void fail(String message) {
        this.status = ExportStatus.FAILED;
        this.error = message == null ? null : message.substring(0, Math.min(message.length(), 500));
    }

    public void expire() {
        this.status = ExportStatus.EXPIRED;
        this.objectName = null;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public ExportKind getKind() {
        return kind;
    }

    public ExportStatus getStatus() {
        return status;
    }

    public int getTotal() {
        return total;
    }

    public int getPrepared() {
        return prepared;
    }

    public String getObjectName() {
        return objectName;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public int getSkipped() {
        return skipped;
    }

    public String getError() {
        return error;
    }

    public com.onlinesanta.closing.doc.ClosingStage getStage() {
        return stage;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
