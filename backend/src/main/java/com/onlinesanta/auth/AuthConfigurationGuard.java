package com.onlinesanta.auth;

import java.util.Arrays;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 啟動時檢查身分驗證的設定，組合有問題就讓應用程式起不來。
 *
 * <p>「設定檔應該不會弄錯」不是一道防線。這兩種錯誤在執行期都不會有明顯症狀——
 * 一個是安靜地開了後門，另一個是安靜地讓所有登入都失敗——寧可部署當場失敗，
 * 也不要帶著它們上線。
 */
@Component
public class AuthConfigurationGuard {

    private final Environment environment;
    private final AuthProperties properties;

    public AuthConfigurationGuard(Environment environment, AuthProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @PostConstruct
    void verify() {
        // 用 matchesProfiles 而非 getActiveProfiles：後者不含「預設 profile」，
        // 而本機正是靠 spring.profiles.default=local 搭配 profiles.group 帶出 dev-auth 的，
        // 讀 getActiveProfiles 會得到空陣列而誤判。
        boolean devAuth = environment.matchesProfiles("dev-auth");

        if (environment.matchesProfiles("prod") && devAuth) {
            throw new IllegalStateException(
                    "prod 與 dev-auth profile 不可同時啟用：dev-auth 允許以標頭偽裝任意身分。"
                            + " 目前啟用的 profile：" + Arrays.toString(environment.getActiveProfiles()));
        }

        // 沒有（或太短的）密鑰，access token 要嘛簽不出來、要嘛可以被猜出來偽造。
        // dev-auth 環境可以只靠標頭運作，AccessTokenService 會自己產生一把暫時的，因此不強制。
        if (!devAuth && !properties.hasUsableSecret()) {
            throw new IllegalStateException(
                    "未設定 app.auth.jwt-secret（環境變數 APP_AUTH_JWT_SECRET），或長度不足 %d 個字元，"
                            .formatted(AuthProperties.MIN_SECRET_LENGTH)
                            + "無法安全地簽發登入用的 token");
        }
    }
}
