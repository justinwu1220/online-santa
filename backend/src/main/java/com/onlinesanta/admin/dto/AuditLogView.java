package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLog;
import com.onlinesanta.admin.AdminAuditTargetType;

/**
 * 一筆稽核紀錄。
 *
 * <p>刻意在監控中心對所有管理員公開：管理員之間互相看得到彼此的操作，
 * 才不會變成「有權限的人可以安靜地做任何事」。
 */
public record AuditLogView(
        Long id,
        String adminEmail,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        UUID targetId,
        String detail,
        Instant occurredAt) {

    public static AuditLogView from(AdminAuditLog log, String adminEmail) {
        return new AuditLogView(
                log.getId(),
                adminEmail,
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetail(),
                log.getCreatedAt());
    }
}
