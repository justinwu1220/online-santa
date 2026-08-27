package com.onlinesanta.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 排程 token 的驗證規則。
 *
 * <p>用單元測試而非整合測試：這些規則是純函式，直接餵 {@link Jwt} 物件進去比架一個
 * 假的 Google OIDC 端點簡單得多，涵蓋的情境也更完整。
 */
@DisplayName("Cloud Scheduler token 的驗證規則")
class SchedulerTokenValidatorTest {

    private static final String AUDIENCE = "https://online-santa-api.run.app";
    private static final String SCHEDULER = "scheduler@online-santa.iam.gserviceaccount.com";

    private final InternalJobProperties properties =
            new InternalJobProperties(AUDIENCE, SCHEDULER);

    private Jwt tokenWith(String issuer, String audience, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("1234567890")
                .claim("email", email)
                .claim("email_verified", true)
                .issuedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();
    }

    private boolean accepts(Jwt token) {
        return !InternalJobSecurityConfig.schedulerTokenValidator(properties)
                .validate(token).hasErrors();
    }

    @Test
    @DisplayName("我們指定的服務帳號簽出的 token 會通過")
    void acceptsTheConfiguredSchedulerAccount() {
        assertThat(accepts(tokenWith(
                InternalJobSecurityConfig.GOOGLE_ISSUER, AUDIENCE, SCHEDULER))).isTrue();
    }

    @Test
    @DisplayName("audience 不符會被拒絕")
    void rejectsTokensMintedForAnotherService() {
        // 少了 audience 檢查，任何 Google 帳號取得的 OIDC token 都能打進排程端點
        assertThat(accepts(tokenWith(
                InternalJobSecurityConfig.GOOGLE_ISSUER,
                "https://someone-elses-service.run.app",
                SCHEDULER))).isFalse();
    }

    @Test
    @DisplayName("其他服務帳號會被拒絕")
    void rejectsOtherServiceAccounts() {
        assertThat(accepts(tokenWith(
                InternalJobSecurityConfig.GOOGLE_ISSUER, AUDIENCE,
                "someone-else@other-project.iam.gserviceaccount.com"))).isFalse();
    }

    @Test
    @DisplayName("沒有 email claim 會被拒絕")
    void rejectsTokensWithoutAnEmailClaim() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(InternalJobSecurityConfig.GOOGLE_ISSUER)
                .audience(List.of(AUDIENCE))
                .claims(claims -> claims.remove("email"))
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();

        assertThat(accepts(token)).isFalse();
    }

    @Test
    @DisplayName("issuer 不是 Google 會被拒絕")
    void rejectsTokensFromAnotherIssuer() {
        assertThat(accepts(tokenWith(
                "https://securetoken.google.com/online-santa", AUDIENCE, SCHEDULER))).isFalse();
    }

    @Test
    @DisplayName("過期的 token 會被拒絕")
    void rejectsExpiredTokens() {
        Jwt expired = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(InternalJobSecurityConfig.GOOGLE_ISSUER)
                .audience(List.of(AUDIENCE))
                .claim("email", SCHEDULER)
                .issuedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        assertThat(accepts(expired)).isFalse();
    }

    @Test
    @DisplayName("服務帳號比對忽略大小寫")
    void serviceAccountComparisonIsCaseInsensitive() {
        assertThat(accepts(tokenWith(
                InternalJobSecurityConfig.GOOGLE_ISSUER, AUDIENCE,
                SCHEDULER.toUpperCase(java.util.Locale.ROOT)))).isTrue();
    }

    @Test
    @DisplayName("claims 的組合必須同時成立")
    void allChecksMustPassTogether() {
        Map<String, String> wrongCombinations = Map.of(
                "wrong-audience", "https://elsewhere.run.app",
                "wrong-email", "attacker@evil.example");

        assertThat(accepts(tokenWith(InternalJobSecurityConfig.GOOGLE_ISSUER,
                wrongCombinations.get("wrong-audience"), SCHEDULER))).isFalse();
        assertThat(accepts(tokenWith(InternalJobSecurityConfig.GOOGLE_ISSUER,
                AUDIENCE, wrongCombinations.get("wrong-email")))).isFalse();
    }
}
