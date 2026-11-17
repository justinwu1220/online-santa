package com.onlinesanta.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 身分驗證相關設定（{@code app.auth.*}）。
 *
 * @param jwtSecret       簽發 access token 與雜湊驗證碼用的密鑰（至少 32 個字元）。
 *                        正式環境必填（{@link AuthConfigurationGuard} 會檢查）；dev-auth 沒設定時
 *                        {@link AccessTokenService} 每次啟動隨機產生一把，重啟後舊 token 即失效，
 *                        本機開發不受影響
 * @param accessTokenTtl  access token 有效時間，預設 15 分鐘
 * @param refreshTokenTtl refresh token（登入狀態）有效時間，預設 30 天
 * @param cookieSecure    refresh cookie 是否加 Secure。正式環境（https）必須為 true；
 *                        本機 http 開發設 false
 * @param adminEmails     平台管理員白名單。名單內的 email 信箱驗證後即取得 ADMIN 角色
 * @param allowedOrigins  允許跨來源請求的前端網域
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure,
        List<String> adminEmails,
        List<String> allowedOrigins) {

    /** HS256 至少要 256 位元的密鑰。 */
    public static final int MIN_SECRET_LENGTH = 32;

    public AuthProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(30);
        }
    }

    /** 密鑰是否合格（有設定且長度足夠）。 */
    public boolean hasUsableSecret() {
        return StringUtils.hasText(jwtSecret)
                && jwtSecret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_LENGTH;
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
