package com.onlinesanta.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;

@DisplayName("Firebase ID token 驗證")
class AuthenticationIT extends ApiIntegrationTest {

    private static final String DONOR = "newcomer@example.com";
    private static final String ADMIN = "platform-admin@example.com";

    @Autowired
    UserRepository users;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            withToken(String token) {
        return get("/api/me").header("Authorization", "Bearer " + token);
    }

    // ------------------------------------------------------------ token 驗證

    @Test
    @DisplayName("有效的 token 會通過")
    void acceptsValidToken() throws Exception {
        mvc.perform(as(get("/api/me"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DONOR))
                .andExpect(jsonPath("$.role").value("DONOR"));
    }

    @Test
    @DisplayName("沒有 token 回 401，且格式與其他錯誤一致")
    void rejectsMissingToken() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("請先登入"));
    }

    @Test
    @DisplayName("別人簽的 token 會被拒絕")
    void rejectsTokenSignedByAnotherKey() throws Exception {
        mvc.perform(withToken(TestJwtSupport.tokenSignedByStranger(DONOR)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("過期的 token 會被拒絕")
    void rejectsExpiredToken() throws Exception {
        String expired = TestJwtSupport.tokenFor(DONOR, DONOR,
                TestJwtSupport.ISSUER, TestJwtSupport.PROJECT_ID,
                Instant.now().minus(1, ChronoUnit.HOURS));

        mvc.perform(withToken(expired)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("其他 Firebase 專案簽的 token 會被拒絕")
    void rejectsTokenFromAnotherFirebaseProject() throws Exception {
        // Firebase 全域共用同一組簽章金鑰，因此 audience 檢查是必要的：
        // 少了它，任何人開一個自己的 Firebase 專案就能簽出我們會接受的 token
        String foreign = TestJwtSupport.tokenFor(DONOR, DONOR,
                "https://securetoken.google.com/someone-elses-project",
                "someone-elses-project",
                Instant.now().plus(1, ChronoUnit.HOURS));

        mvc.perform(withToken(foreign)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("格式不對的 token 會被拒絕")
    void rejectsMalformedToken() throws Exception {
        mvc.perform(withToken("this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ 帳號建立

    @Test
    @DisplayName("首次登入就地建立本地帳號")
    void provisionsLocalAccountOnFirstLogin() throws Exception {
        assertThat(users.findByEmailIgnoreCase(DONOR)).isEmpty();

        mvc.perform(as(get("/api/me"), DONOR)).andExpect(status().isOk());

        var created = users.findByEmailIgnoreCase(DONOR).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.DONOR);
        assertThat(created.getFirebaseUid()).isEqualTo(TestJwtSupport.uidFor(DONOR));
        assertThat(created.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("再次登入沿用同一個帳號")
    void reusesTheSameAccountOnSubsequentLogins() throws Exception {
        mvc.perform(as(get("/api/me"), DONOR)).andExpect(status().isOk());
        var first = users.findByEmailIgnoreCase(DONOR).orElseThrow().getId();

        mvc.perform(as(get("/api/me"), DONOR)).andExpect(status().isOk());
        assertThat(users.findByEmailIgnoreCase(DONOR).orElseThrow().getId()).isEqualTo(first);
        assertThat(users.count()).isOne();
    }

    @Test
    @DisplayName("設定檔白名單內的 email 登入即為管理員")
    void grantsAdminRoleToWhitelistedEmail() throws Exception {
        mvc.perform(as(get("/api/me"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.organizationId").doesNotExist());
    }

    // ------------------------------------------------------------ 公開端點

    @Test
    @DisplayName("願望牆不需要登入")
    void wishWallStaysPublic() throws Exception {
        mvc.perform(get("/api/wishes")).andExpect(status().isOk());
        mvc.perform(get("/api/wishes/options")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("健康檢查與 API 文件不需要登入")
    void operationalEndpointsStayPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("寫入操作一律需要登入")
    void writeOperationsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/organizations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/organizations/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("開發用的標頭在正式驗證下無效")
    void developmentHeaderCarriesNoAuthorityWhenDevAuthIsOff() throws Exception {
        // 測試環境沒有啟用 dev-auth profile，這個標頭應該完全不起作用
        mvc.perform(get("/api/me").header("X-Dev-User-Email", "attacker@example.com"))
                .andExpect(status().isUnauthorized());
    }
}
