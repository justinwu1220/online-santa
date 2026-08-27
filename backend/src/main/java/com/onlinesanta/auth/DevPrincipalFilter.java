package com.onlinesanta.auth;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 開發用的身分模擬——依 {@code X-Dev-User-Email} 標頭認人，完全沒有驗證。
 *
 * <p>存在的理由是本機用 curl 測 API 時，取得真實 Firebase ID token 得先走完整個
 * 瀏覽器登入流程，過於麻煩。
 *
 * <p><strong>安全性：</strong>只在 {@code dev-auth} profile 啟用，而該 profile 僅由
 * application.yml 的 profiles.group 在 local 時帶出；正式環境跑 prod，兩者不會同時成立。
 * {@link AuthConfigurationGuard} 會在啟動時再檢查一次，組合錯誤就直接讓應用程式啟動失敗。
 *
 * <p>已帶 Authorization 標頭的請求會跳過這個 filter，讓真實 token 永遠優先。
 *
 * <p>刻意不加 {@code @Component} 自動註冊：那樣它會被掛在 Spring Security 過濾鏈
 * <em>之後</em>，請求早就被判定未登入而回 401 了。改由 {@link SecurityConfig} 明確地
 * 插進安全鏈中 BearerTokenAuthenticationFilter 之前。
 */
@Profile("dev-auth")
public class DevPrincipalFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DevPrincipalFilter.class);

    public static final String EMAIL_HEADER = "X-Dev-User-Email";
    public static final String NAME_HEADER = "X-Dev-User-Name";

    private final UserProvisioningService provisioning;

    public DevPrincipalFilter(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
        log.warn("=".repeat(78));
        log.warn("dev-auth profile 已啟用：任何請求都能以 {} 標頭偽裝身分。", EMAIL_HEADER);
        log.warn("這只應出現在本機開發環境。");
        log.warn("=".repeat(78));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String email = request.getHeader(EMAIL_HEADER);

        if (StringUtils.hasText(email) && !StringUtils.hasText(request.getHeader("Authorization"))) {
            AppPrincipal principal = provisioning.resolve(
                    "dev-" + email.trim(), email.trim(), request.getHeader(NAME_HEADER));
            SecurityContextHolder.getContext()
                    .setAuthentication(new AppAuthentication(null, principal));
        }

        // 不需要自行清理：安全鏈的 SecurityContextHolderFilter 已經在 finally 裡清了
        chain.doFilter(request, response);
    }
}
