package com.onlinesanta.config;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

/**
 * 提供 JPA Auditing 的「目前操作者」。
 *
 * <p>M3 導入 Firebase Auth 後會改為從 SecurityContext 取出 {@code AppPrincipal} 的 user id；
 * 在那之前一律回傳 empty，稽核欄位維持 null。
 */
@Configuration
public class JpaAuditingConfig {

    @Bean
    AuditorAware<UUID> auditorAware() {
        return Optional::empty;
    }
}
