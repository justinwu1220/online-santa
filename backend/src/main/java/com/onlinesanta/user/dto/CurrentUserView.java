package com.onlinesanta.user.dto;

import java.util.UUID;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.user.UserRole;

/**
 * 前端用來決定顯示哪些功能的身分資訊。
 *
 * <p>取代 Firebase custom claims：claims 要等 ID token 重簽（最長一小時）才會更新，
 * 這個端點永遠是最新的，而且不需要引入 Firebase Admin SDK 與服務帳號金鑰。
 */
public record CurrentUserView(
        UUID userId,
        String email,
        UserRole role,
        UUID organizationId,
        /**
         * 信箱未驗證時，實際權限會降為一般民眾——前端要知道這件事，
         * 才能說明「管理功能為什麼不見了」而不是讓人以為系統壞了。
         */
        boolean emailVerified,
        /** 認領者被停權（受限登入）：前端顯示停權通知，寫入動作都會被後端擋下 */
        boolean suspended,
        /** 停權原因；沒有停權為 null */
        String suspendedReason) {

    public static CurrentUserView from(AppPrincipal principal) {
        return from(principal, null);
    }

    public static CurrentUserView from(AppPrincipal principal, String suspendedReason) {
        return new CurrentUserView(
                principal.userId(), principal.email(), principal.role(),
                principal.organizationId(), principal.emailVerified(),
                principal.suspended(), suspendedReason);
    }
}
