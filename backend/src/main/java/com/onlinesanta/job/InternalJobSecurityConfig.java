package com.onlinesanta.job;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.onlinesanta.auth.ApiAccessDeniedHandler;
import com.onlinesanta.auth.ApiAuthenticationEntryPoint;

/**
 * {@code /internal/**} 的存取控制。
 *
 * <p>這條鏈與一般使用者的驗證完全分開，因為 token 的來源根本不同：呼叫者是
 * Cloud Scheduler，帶的是 Google 簽發的 OIDC token（issuer 為
 * {@code https://accounts.google.com}），而不是 Firebase 的 ID token。用同一條鏈處理
 * 兩種 issuer 會讓兩邊的驗證規則互相牽制。
 *
 * <p>{@code @Order(1)} 讓它先於一般的安全鏈比對，只接管 {@code /internal/**}。
 */
@Configuration
public class InternalJobSecurityConfig {

    static final String GOOGLE_ISSUER = "https://accounts.google.com";
    static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final InternalJobProperties properties;

    public InternalJobSecurityConfig(InternalJobProperties properties) {
        this.properties = properties;
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalJobSecurityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint entryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) throws Exception {

        return http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(googleOidcJwtDecoder()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /**
     * 驗證 Cloud Scheduler 的 OIDC token。公鑰由 Google 的憑證端點取得並快取。
     *
     * <p>刻意不做成 Spring bean：只有這條鏈用得到，而多一個 {@code JwtDecoder} 型別的
     * bean 會讓「哪個解碼器給誰用」變成必須靠 qualifier 才說得清楚的事。
     */
    private JwtDecoder googleOidcJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(GOOGLE_JWK_SET_URI)
                .build();
        decoder.setJwtValidator(schedulerTokenValidator(properties));
        return decoder;
    }

    /**
     * 除了簽章與有效期外還檢查兩件事：
     *
     * <ul>
     *   <li><b>audience</b>——設定成本服務的網址。少了它，任何 Google 帳號取得的
     *       OIDC token 都能打進來</li>
     *   <li><b>email</b>——必須是設定中指定的服務帳號。就算 audience 相符，也只有
     *       我們指定的那個身分能觸發排程</li>
     * </ul>
     */
    public static OAuth2TokenValidator<Jwt> schedulerTokenValidator(
            InternalJobProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(properties.audience())),
                new JwtClaimValidator<String>("email",
                        email -> email != null
                                && email.equalsIgnoreCase(properties.schedulerServiceAccount())));
    }
}
