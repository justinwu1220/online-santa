package com.onlinesanta.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
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

    /**
     * 測試用的記錄式郵件寄送器，取代真的 SMTP 連線。
     *
     * <p>正式環境的 {@code MailConfig.javaMailSender} 在 {@code MAIL_HOST} 未設定時
     * 回傳 {@code null}（no-op 降級），測試環境同樣沒有設定，但這裡改註冊一個
     * {@code @Primary} 的記錄式實作，讓 IT 能斷言「哪些信被寄出去了」，而不是
     * 停留在「反正沒有 mailSender，程式不會炸」這種弱驗證。
     */
    @Bean
    @Primary
    JavaMailSender testJavaMailSender() {
        return new RecordingMailSender();
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
