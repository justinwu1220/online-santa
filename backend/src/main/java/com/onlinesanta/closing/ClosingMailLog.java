package com.onlinesanta.closing;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 結案寄信紀錄（機構確認表）。（檔期、種類、對象）唯一：同一機構同一種信只有一列，重寄是更新這一列，
 * 不會重複寄出。只記收件信箱與狀態，不存附件。
 */
@Entity
@Table(name = "closing_mail_log")
public class ClosingMailLog extends BaseEntity {

    public static final String KIND_ORG_FORMS = "ORG_FORMS";

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(nullable = false, updatable = false, length = 20)
    private String kind;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 300)
    private String error;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected ClosingMailLog() {
    }

    public ClosingMailLog(UUID campaignId, String kind, UUID targetId, String recipient) {
        this.campaignId = campaignId;
        this.kind = kind;
        this.targetId = targetId;
        this.recipient = recipient;
        this.status = "FAILED";
    }

    public void markSent(String recipient, Instant at) {
        this.recipient = recipient;
        this.status = "SENT";
        this.error = null;
        this.sentAt = at;
    }

    public void markFailed(String recipient, String error) {
        this.recipient = recipient;
        this.status = "FAILED";
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 300));
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
