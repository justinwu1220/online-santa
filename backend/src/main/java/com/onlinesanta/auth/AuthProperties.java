package com.onlinesanta.auth;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 身分驗證相關設定（{@code app.auth.*}）。
 *
 * @param firebaseProjectId Firebase 專案 ID。ID token 的 issuer 與 audience 都由它推導
 * @param adminEmails       平台管理員白名單。名單內的 email 首次登入即取得 ADMIN 角色
 * @param allowedOrigins    允許跨來源請求的前端網域
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String firebaseProjectId,
        List<String> adminEmails,
        List<String> allowedOrigins) {

    /** Firebase ID token 的簽發者。 */
    public String issuer() {
        return "https://securetoken.google.com/" + firebaseProjectId;
    }

    /**
     * Firebase 用來簽 ID token 的公鑰。
     *
     * <p>由 Spring Security 快取並依 HTTP 快取標頭自動更新，因此驗證 token 不需要
     * 每次請求都連到 Google。
     */
    public String jwkSetUri() {
        return "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
    }

    /** 比對一律忽略大小寫——email 的網域部分本來就不分大小寫。 */
    public Set<String> normalisedAdminEmails() {
        return adminEmails == null ? Set.of() : adminEmails.stream()
                .filter(email -> !email.isBlank())
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdminEmail(String email) {
        return email != null && normalisedAdminEmails().contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
