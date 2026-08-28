package com.onlinesanta.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 產生測試用的 Firebase ID token。
 *
 * <p>用真實的 RSA 金鑰簽出真的 JWT，而不是用 {@code SecurityMockMvcRequestPostProcessors.jwt()}
 * 直接把身分塞進 SecurityContext——後者會跳過解碼器與轉換器，那正是最需要被測到的部分
 * （簽章驗證、issuer/audience 檢查、JIT 建立帳號、由資料庫決定權限）。
 */
public final class TestJwtSupport {

    public static final String PROJECT_ID = "online-santa-test";
    public static final String ISSUER = "https://securetoken.google.com/" + PROJECT_ID;

    private static final KeyPair KEY_PAIR = generateKeyPair();

    private TestJwtSupport() {
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("無法產生測試用的 RSA 金鑰", e);
        }
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    /** 依 email 推導出穩定的 Firebase uid，讓同一個 email 在測試中始終對應同一個帳號。 */
    public static String uidFor(String email) {
        return "uid-" + email;
    }

    /** 簽出一個有效的 ID token。 */
    public static String tokenFor(String email) {
        return tokenFor(email, email, ISSUER, PROJECT_ID, Instant.now().plus(1, ChronoUnit.HOURS));
    }

    public static String tokenFor(String email, String displayName) {
        return tokenFor(email, displayName, ISSUER, PROJECT_ID,
                Instant.now().plus(1, ChronoUnit.HOURS));
    }

    /** 完整參數版，供測試 issuer / audience / 過期等失敗情境使用。 */
    public static String tokenFor(String email, String displayName,
                                  String issuer, String audience, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(uidFor(email))
                    .issuer(issuer)
                    .audience(audience)
                    .claim("email", email)
                    .claim("email_verified", true)
                    .claim("name", displayName)
                    .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                    .expirationTime(Date.from(expiresAt))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("無法簽出測試用的 ID token", e);
        }
    }

    /**
     * 簽出一個信箱「未驗證」的 token——對應密碼註冊但還沒點驗證信的使用者。
     *
     * <p>Google 登入永遠是已驗證，只有密碼註冊會出現這個狀態。
     */
    public static String unverifiedTokenFor(String email) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(uidFor(email))
                    .issuer(ISSUER)
                    .audience(PROJECT_ID)
                    .claim("email", email)
                    .claim("email_verified", false)
                    .claim("name", email)
                    .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                    .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("無法簽出測試用的 token", e);
        }
    }

    /**
     * 同一個信箱、不同的 Firebase uid，且未驗證。
     *
     * <p>用來模擬「拿別人的信箱去註冊密碼帳號」的接管攻擊。
     */
    public static String unverifiedTokenWithForeignUid(String email) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("attacker-uid-" + email)
                    .issuer(ISSUER)
                    .audience(PROJECT_ID)
                    .claim("email", email)
                    .claim("email_verified", false)
                    .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("無法簽出測試用的 token", e);
        }
    }

    /** 用另一組金鑰簽出的 token，用來驗證簽章不符會被拒絕。 */
    public static String tokenSignedByStranger(String email) {
        try {
            KeyPair strangerKey = generateKeyPair();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(uidFor(email))
                    .issuer(ISSUER)
                    .audience(PROJECT_ID)
                    .claim("email", email)
                    .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) strangerKey.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("無法簽出測試用的 token", e);
        }
    }
}
