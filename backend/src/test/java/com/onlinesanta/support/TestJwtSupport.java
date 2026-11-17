package com.onlinesanta.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 產生測試用的 access token。
 *
 * <p>用真實的密鑰簽出真的 JWT，而不是用 {@code SecurityMockMvcRequestPostProcessors.jwt()}
 * 直接把身分塞進 SecurityContext——後者會跳過解碼器與轉換器，那正是最需要被測到的部分
 * （簽章驗證、issuer 與有效期檢查、由資料庫決定權限與信箱是否驗證）。
 *
 * <p>密鑰與 {@code application-test.yml} 的 {@code app.auth.jwt-secret} 相同。
 */
public final class TestJwtSupport {

    public static final String ISSUER = "online-santa";
    public static final String SECRET = "test-only-secret-for-integration-tests-0123456789abcdef";

    private TestJwtSupport() {
    }

    /** 簽出一個有效的 access token。 */
    public static String tokenFor(UUID userId) {
        return tokenFor(userId, ISSUER, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    /** 完整參數版，供測試 issuer 與過期等失敗情境使用。 */
    public static String tokenFor(UUID userId, String issuer, Instant expiresAt) {
        return sign(SECRET, userId.toString(), issuer, expiresAt);
    }

    /** 任意的 subject，用來測試 sub 不是合法使用者 id 的情況。 */
    public static String tokenWithSubject(String subject) {
        return sign(SECRET, subject, ISSUER, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    /** 用另一把密鑰簽出的 token，用來驗證簽章不符會被拒絕。 */
    public static String tokenSignedByStranger(UUID userId) {
        return sign("another-secret-another-secret-another-secret-00", userId.toString(), ISSUER,
                Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    private static String sign(String secret, String subject, String issuer, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                    .expirationTime(Date.from(expiresAt))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("無法簽出測試用的 token", e);
        }
    }
}
