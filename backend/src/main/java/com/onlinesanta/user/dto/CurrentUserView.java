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
        UUID organizationId) {

    public static CurrentUserView from(AppPrincipal principal) {
        return new CurrentUserView(
                principal.userId(), principal.email(), principal.role(), principal.organizationId());
    }
}
