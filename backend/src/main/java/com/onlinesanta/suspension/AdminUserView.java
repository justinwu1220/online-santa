package com.onlinesanta.suspension;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.user.User;

/**
 * 管理員看到的認領者：信箱、顯示名稱、真實姓名與停權狀態。只在管理端點回傳，
 * 真實姓名等個資不會出現在機構端或認領者端的任何回應。
 */
public record AdminUserView(
        UUID id,
        String email,
        String displayName,
        String name,
        Instant createdAt,
        boolean suspended,
        Instant suspendedAt,
        String suspendedReason) {

    public static AdminUserView from(User user) {
        return new AdminUserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getName(),
                user.getCreatedAt(), user.isSuspended(), user.getSuspendedAt(), user.getSuspendedReason());
    }
}
