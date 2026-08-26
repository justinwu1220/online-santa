package com.onlinesanta.auth;

import java.util.UUID;

import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRole;

/**
 * 目前請求的操作者。刻意做成不可變的快照，不讓 controller 拿到可修改的 entity。
 */
public record AppPrincipal(UUID userId, String email, UserRole role, UUID organizationId) {

    public static AppPrincipal from(User user) {
        return new AppPrincipal(user.getId(), user.getEmail(), user.getRole(), user.getOrganizationId());
    }

    public boolean isOrgMember() {
        return role == UserRole.ORG_MEMBER;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
