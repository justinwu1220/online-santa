package com.onlinesanta.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * 簽發與驗證 access token（短效的 HS256 JWT）。
 *
 * <p>token 只放「這是誰」（{@code sub} = 使用者 id）。角色、信箱是否驗證一律每次請求回資料庫查，
 * 所以管理員核准機構、使用者剛完成信箱驗證，下一個請求就生效，不必等 token 換發。
 */
@Service
public class AccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenService.class);

    public static final String ISSUER = "online-santa";

    private final AuthProperties properties;
    private final SecretKey key;
    private final JwtEncoder encoder;

    public AccessTokenService(AuthProperties properties) {
        this.properties = properties;
        this.key = new SecretKeySpec(secretBytes(properties), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    private static byte[] secretBytes(AuthProperties properties) {
        if (properties.hasUsableSecret()) {
            return properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        }
        // 走到這裡只可能是 dev-auth（AuthConfigurationGuard 擋掉了其他情況）
        log.warn("app.auth.jwt-secret 未設定或太短，改用本次啟動隨機產生的密鑰：重啟後所有登入狀態失效。");
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        return random;
    }

    /** 簽發給指定使用者的 access token。 */
    public IssuedToken issue(UUID userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(value, properties.accessTokenTtl().toSeconds());
    }

    /** 給 Spring Security 的解碼器：驗簽章、有效期與 issuer。 */
    public JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(ISSUER);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** 用同一把密鑰做 HMAC（驗證碼雜湊用）。 */
    SecretKey hmacKey() {
        return key;
    }

    /** @param expiresInSeconds 距離過期還有幾秒，前端用來決定何時換發 */
    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
