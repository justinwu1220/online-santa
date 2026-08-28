package com.onlinesanta.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * API 的存取規則與 Firebase ID token 的驗證設定。
 *
 * <p>願望牆是刻意公開的：民眾要能先看見孩子們的願望，才會考慮註冊。
 * 其餘端點一律需要身分。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthProperties properties;
    private final Environment environment;

    public SecurityConfig(AuthProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * 一般 API 的安全鏈。{@code @Order(2)} 排在 {@code /internal/**} 那條之後——
     * 排程端點走的是 Google OIDC，與這裡的 Firebase 驗證是兩套完全不同的規則。
     */
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtDecoder decoder,
                                            FirebaseAuthenticationConverter converter,
                                            ApiAuthenticationEntryPoint entryPoint,
                                            ApiAccessDeniedHandler accessDeniedHandler,
                                            Optional<DevPrincipalFilter> devPrincipalFilter)
            throws Exception {
        // 只有 dev-auth profile 才會有這個 bean。插在 Bearer token 驗證之前，
        // 使真實 token 一旦存在就永遠優先。
        devPrincipalFilter.ifPresent(filter ->
                http.addFilterBefore(filter, BearerTokenAuthenticationFilter.class));

        return http
                // 沒有 cookie 就沒有 CSRF 的攻擊面；身分完全由 Authorization 標頭承載
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 公開瀏覽：未登入也看得到願望牆
                        .requestMatchers(HttpMethod.GET, "/api/wishes", "/api/wishes/options",
                                "/api/wishes/*").permitAll()
                        // 維運與 API 文件
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()
                        // 本機開發用的假 Cloud Storage：只在 dev-storage profile 存在，
                        // 由 URL 簽章保護而非登入狀態——與正式環境的 GCS 行為一致
                        .requestMatchers("/dev-storage/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(decoder)
                                .jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /**
     * Firebase ID token 的解碼與驗證。
     *
     * <p>公鑰由 Google 的 JWKS 端點取得並由 Spring 快取，因此驗證 token 不需要每個
     * 請求都連外。除了簽章與有效期，還必須檢查 audience：Firebase 全域共用同一組
     * 簽章金鑰，少了這一步，別的 Firebase 專案簽出的 token 也會被我們接受。
     */
    @Bean
    JwtDecoder firebaseJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .build();
        decoder.setJwtValidator(firebaseTokenValidator(properties));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> firebaseTokenValidator(AuthProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(properties.firebaseProjectId())));
    }

    /**
     * 跨來源規則。
     *
     * <p>正式環境只有 API 需要——檔案是直傳到 Cloud Storage，那邊的 CORS 由
     * bucket 自己的設定負責（見 {@code docs/DEPLOY.md}）。
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", apiCors());

        // 本機的假 Cloud Storage 是同一個 Spring 應用程式，因此它的 CORS 也得由我們
        // 出。正式環境對應的是 bucket 上的 CORS 設定——漏了這一段，本機的直傳會在
        // 瀏覽器的預檢就被擋下（curl 測不出來，因為 curl 沒有同源政策）
        if (environment.matchesProfiles("dev-storage")) {
            source.registerCorsConfiguration("/dev-storage/**", devStorageCors());
        }
        return source;
    }

    private CorsConfiguration apiCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        return config;
    }

    /**
     * 對齊 {@code docs/DEPLOY.md} 裡給 bucket 的 CORS：直傳用 PUT，讀取用 GET。
     *
     * <p>不放 Authorization——直傳的授權完全來自網址上的簽章，帶 token 過去反而
     * 是把憑證交給儲存端，正式環境的 GCS 也不吃。
     */
    private CorsConfiguration devStorageCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type"));
        config.setMaxAge(3600L);
        return config;
    }
}
