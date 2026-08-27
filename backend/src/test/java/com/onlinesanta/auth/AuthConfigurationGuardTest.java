package com.onlinesanta.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("身分驗證設定的啟動檢查")
class AuthConfigurationGuardTest {

    private AuthProperties propertiesWithProjectId(String projectId) {
        return new AuthProperties(projectId, List.of(), List.of("http://localhost:5173"));
    }

    private AuthConfigurationGuard guardFor(AuthProperties properties, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new AuthConfigurationGuard(environment, properties);
    }

    @Test
    @DisplayName("prod 與 dev-auth 同時啟用時拒絕啟動")
    void refusesToStartWhenDevAuthIsEnabledInProduction() {
        var guard = guardFor(propertiesWithProjectId("real-project"), "prod", "dev-auth");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可同時啟用");
    }

    @Test
    @DisplayName("正式環境缺少 Firebase 專案 ID 時拒絕啟動")
    void refusesToStartWithoutFirebaseProjectId() {
        var guard = guardFor(propertiesWithProjectId("  "), "prod");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("firebase-project-id");
    }

    @Test
    @DisplayName("開發環境可以只靠 dev-auth 運作")
    void allowsDevAuthWithoutFirebaseProjectId() {
        var guard = guardFor(propertiesWithProjectId(null), "local", "dev-auth");

        assertThatCode(guard::verify).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev-auth 來自預設 profile 時同樣算數")
    void recognisesDevAuthComingFromDefaultProfiles() {
        // 本機是靠 spring.profiles.default=local 搭配 profiles.group 帶出 dev-auth，
        // 此時 getActiveProfiles() 是空的——檢查必須看得到預設 profile
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("local", "dev-auth");
        var guard = new AuthConfigurationGuard(environment, propertiesWithProjectId(null));

        assertThatCode(guard::verify).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("設定正確的正式環境可以啟動")
    void acceptsAProperlyConfiguredProductionEnvironment() {
        var guard = guardFor(propertiesWithProjectId("online-santa-prod"), "prod");

        assertThatCode(guard::verify).doesNotThrowAnyException();
    }
}
