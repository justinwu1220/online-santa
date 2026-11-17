package com.onlinesanta.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DisplayName("access token 驗證")
class AuthenticationIT extends ApiIntegrationTest {

    private static final String DONOR = "newcomer@example.com";
    private static final String ADMIN = "platform-admin@example.com";

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager em;

    private User newUser(String email) {
        User user = User.newDonor(email, email);
        user.markEmailVerified(Instant.now());
        return users.save(user);
    }

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
        mvc.perform(withToken(TestJwtSupport.tokenSignedByStranger(newUser(DONOR).getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("過期的 token 會被拒絕")
    void rejectsExpiredToken() throws Exception {
        String expired = TestJwtSupport.tokenFor(newUser(DONOR).getId(),
                TestJwtSupport.ISSUER, Instant.now().minus(1, ChronoUnit.HOURS));

        mvc.perform(withToken(expired)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("別的簽發者的 token 會被拒絕")
    void rejectsTokenFromAnotherIssuer() throws Exception {
        String foreign = TestJwtSupport.tokenFor(newUser(DONOR).getId(),
                "someone-else", Instant.now().plus(1, ChronoUnit.HOURS));

        mvc.perform(withToken(foreign)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sub 不是使用者 id 的 token 會被拒絕")
    void rejectsTokenWhoseSubjectIsNotAUserId() throws Exception {
        mvc.perform(withToken(TestJwtSupport.tokenWithSubject("not-a-uuid")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token 有效但帳號已不存在會被拒絕")
    void rejectsTokenOfAMissingUser() throws Exception {
        mvc.perform(withToken(TestJwtSupport.tokenFor(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("格式不對的 token 會被拒絕")
    void rejectsMalformedToken() throws Exception {
        mvc.perform(withToken("this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("過期的 token 帶在公開的登入端點上，不會擋住登入")
    void expiredTokenDoesNotBlockPublicAuthEndpoints() throws Exception {
        // 前端的 access token 過期後去換發：Spring 預設會在進到 controller 之前就回 401，
        // 結果再也登不回去。公開的帳號端點必須無視 Authorization 標頭
        String expired = TestJwtSupport.tokenFor(newUser(DONOR).getId(),
                TestJwtSupport.ISSUER, Instant.now().minus(1, ChronoUnit.HOURS));

        mvc.perform(withBody(post("/api/auth/login").header("Authorization", "Bearer " + expired),
                        Map.of("email", "nobody@example.com", "password", "whatever12")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    // ------------------------------------------------------------ 身分來自資料庫

    @Test
    @DisplayName("每個請求都記錄最近登入時間")
    void recordsLastLogin() throws Exception {
        newUser(DONOR);

        mvc.perform(as(get("/api/me"), DONOR)).andExpect(status().isOk());

        var user = users.findByEmailIgnoreCase(DONOR).orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.DONOR);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("停用的帳號即使 token 還有效也不能用")
    void disabledAccountIsRejectedEvenWithAValidToken() throws Exception {
        String token = TestJwtSupport.tokenFor(newUser(DONOR).getId());
        em.flush();
        jdbc.update("UPDATE users SET disabled = true WHERE lower(email) = lower(?)", DONOR);
        // 清掉一級快取：不然請求讀到的還是上面那個 disabled = false 的實體
        em.clear();

        mvc.perform(withToken(token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
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
    @DisplayName("驗證碼與重寄需要登入")
    void verificationEndpointsRequireAuthentication() throws Exception {
        mvc.perform(withBody(post("/api/auth/verify-email"), Map.of("code", "123456")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/verification/resend")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("開發用的標頭在正式驗證下無效")
    void developmentHeaderCarriesNoAuthorityWhenDevAuthIsOff() throws Exception {
        // 測試環境沒有啟用 dev-auth profile，這個標頭應該完全不起作用
        mvc.perform(get("/api/me").header("X-Dev-User-Email", "attacker@example.com"))
                .andExpect(status().isUnauthorized());
    }
}
