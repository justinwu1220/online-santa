package com.onlinesanta.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 開發用身分模擬的組裝。
 *
 * <p>{@link DevPrincipalFilter} 只在這裡被建立，因此非 {@code dev-auth} 環境
 * 連這個類別的實例都不存在——不是靠執行期的 if 判斷關掉，而是根本沒被載入。
 */
@Configuration
@Profile("dev-auth")
public class DevAuthConfig {

    @Bean
    DevPrincipalFilter devPrincipalFilter(UserProvisioningService provisioning) {
        return new DevPrincipalFilter(provisioning);
    }
}
