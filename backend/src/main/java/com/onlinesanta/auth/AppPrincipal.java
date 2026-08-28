package com.onlinesanta.auth;

import java.util.UUID;

import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRole;

/**
 * 目前請求的操作者。刻意做成不可變的快照，不讓 controller 拿到可修改的 entity。
 *
 * @param emailVerified 信箱是否已驗證。刻意<strong>不存進資料庫</strong>——這是 token
 *                      的屬性且會隨時間改變（使用者稍後點了驗證信就變了），存起來會過期。
 *                      Google 登入永遠是 true；密碼註冊在使用者點驗證信之前是 false
 */
public record AppPrincipal(
        UUID userId,
        String email,
        UserRole role,
        UUID organizationId,
        boolean emailVerified) {

    public static AppPrincipal from(User user, boolean emailVerified) {
        return new AppPrincipal(
                user.getId(), user.getEmail(), user.getRole(),
                user.getOrganizationId(), emailVerified);
    }

    /**
     * 這次請求實際生效的角色。
     *
     * <p>資料庫裡的 {@link #role()} 是「這個人是誰」，這個方法是「這個 token 能做什麼」。
     * 兩者在信箱未驗證時會不同：角色提升需要驗證過的信箱，但提升是單向的——資料庫的
     * 角色不會因為信箱狀態改變而降級。信箱有可能從已驗證變回未驗證（使用者在 Firebase
     * 更換了信箱），那時舊有的管理員或機構權限不該還能使用。
     *
     * <p>授權一律看這個方法，不要直接看 {@code role()}——有兩套認定就遲早會有一套漏掉。
     */
    public UserRole effectiveRole() {
        return emailVerified ? role : UserRole.DONOR;
    }

    public boolean isOrgMember() {
        return effectiveRole() == UserRole.ORG_MEMBER;
    }

    public boolean isAdmin() {
        return effectiveRole() == UserRole.ADMIN;
    }
}
