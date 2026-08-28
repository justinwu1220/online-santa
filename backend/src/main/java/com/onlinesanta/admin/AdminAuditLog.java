package com.onlinesanta.admin;

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
 * 管理員的一筆敏感操作紀錄。
 *
 * <p>管理員是全系統權限最大的角色——為了處理申訴與排查問題，他看得到捐贈者個資與
 * 含孩童影像的回饋照片。「能看」與「看了不留痕跡」是兩件事，這張表補上後者。
 *
 * <p>比照 {@code ClaimEvent}：bigint 序號主鍵、只增不改，因此不繼承 BaseEntity。
 */
@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false, updatable = false)
    private UUID adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 30)
    private AdminAuditTargetType targetType;

    /** SYSTEM 類的動作可以沒有目標。 */
    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(length = 500, updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditLog() {
        // JPA
    }

    private AdminAuditLog(UUID adminUserId, AdminAuditAction action, UUID targetId, String detail) {
        this.adminUserId = adminUserId;
        this.action = action;
        this.targetType = action.targetType();
        this.targetId = targetId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public static AdminAuditLog of(UUID adminUserId, AdminAuditAction action,
                                   UUID targetId, String detail) {
        return new AdminAuditLog(adminUserId, action, targetId, detail);
    }

    public Long getId() {
        return id;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public AdminAuditAction getAction() {
        return action;
    }

    public AdminAuditTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
