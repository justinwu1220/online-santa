package com.onlinesanta.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.onlinesanta.auth.AuthProperties;
import com.onlinesanta.auth.SecurityConfig;
import com.onlinesanta.storage.ObjectStorage;

/**
 * 測試環境的 JWT 解碼器：改用本地金鑰，不連 Google 的 JWKS 端點。
 *
 * <p>驗證規則（issuer、audience、有效期）刻意沿用正式設定的同一份
 * {@link SecurityConfig#firebaseTokenValidator}，才不會出現「測試過了但正式環境
 * 的驗證其實有漏洞」的情況。
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * 測試用的儲存空間，取代 GCS。
     *
     * <p>整合測試不該依賴外部服務——需要網路、需要憑證、還會留下垃圾檔案。
     */
    @Bean
    @Primary
    ObjectStorage testObjectStorage() {
        return new InMemoryObjectStorage();
    }

    @Bean
    @Primary
    JwtDecoder testJwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(TestJwtSupport.publicKey())
                .build();
        decoder.setJwtValidator(SecurityConfig.firebaseTokenValidator(properties));
        return decoder;
    }
}
