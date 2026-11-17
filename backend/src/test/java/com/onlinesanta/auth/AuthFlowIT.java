package com.onlinesanta.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.RecordingMailSender;
import com.onlinesanta.terms.TermsProperties;
import com.onlinesanta.terms.TermsType;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;

/**
 * 本機帳號流程：註冊、登入、登入狀態、信箱驗證碼、重設密碼。
 *
 * <p>測試方法包在一個不會 commit 的交易裡，{@code AuthMailService} 的「commit 之後才寄」
 * 因此不會觸發。驗證碼與重設連結改用 spy 攔下呼叫參數；真正寄出的信另有一個測試用
 * {@link TestTransaction} 強制 commit 來驗證。
 */
@DisplayName("本機帳號流程")
class AuthFlowIT extends ApiIntegrationTest {

    private static final String EMAIL = "flow@example.com";
    private static final String PASSWORD = "correct-horse-1";

    @MockitoSpyBean
    AuthMailService mail;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TermsProperties termsProperties;

    @Autowired
    LocalAuthService localAuth;

    @Autowired
    org.springframework.mail.javamail.JavaMailSender mailSender;

    @PersistenceContext
    EntityManager em;

    @BeforeEach
    void resetMail() {
        ((RecordingMailSender) mailSender).reset();
    }

    // ------------------------------------------------------------ 輔助

    private MvcResult register(String email, String password) throws Exception {
        return mvc.perform(withBody(post("/api/auth/register"),
                        Map.of("email", email, "password", password)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(withBody(post("/api/auth/login"),
                        Map.of("email", email, "password", password)))
                .andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String accessToken(MvcResult result) throws Exception {
        return body(result).get("accessToken").asText();
    }

    private Cookie sessionCookie(MvcResult result) {
        return result.getResponse().getCookie(AuthController.REFRESH_COOKIE);
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, MvcResult session)
            throws Exception {
        return builder.header("Authorization", "Bearer " + accessToken(session));
    }

    private MockHttpServletRequestBuilder refreshWith(Cookie cookie) {
        return post("/api/auth/refresh").cookie(cookie);
    }

    /** 最近一次寄給這個信箱的驗證碼。 */
    private String lastCodeFor(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).sendVerificationCode(eq(email), code.capture());
        List<String> all = code.getAllValues();
        return all.get(all.size() - 1);
    }

    private String lastResetTokenFor(String email) {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).sendPasswordReset(eq(email), token.capture());
        List<String> all = token.getAllValues();
        return all.get(all.size() - 1);
    }

    private String wrongCodeFor(String realCode) {
        return "000000".equals(realCode) ? "111111" : "000000";
    }

    /** 把資料庫的改動寫出去並清掉快取，之後讀到的才是 jdbc 改過的值。 */
    private void syncDatabase(Runnable jdbcChange) {
        em.flush();
        jdbcChange.run();
        em.clear();
    }

    // ------------------------------------------------------------ 註冊

    @Test
    @DisplayName("註冊建立未驗證的帳號、直接登入，refresh token 只放在 HttpOnly cookie")
    void registerCreatesAnUnverifiedAccountAndSignsIn() throws Exception {
        MvcResult result = register(EMAIL, PASSWORD);

        JsonNode session = body(result);
        assertThat(session.get("email").asText()).isEqualTo(EMAIL);
        assertThat(session.get("emailVerified").asBoolean()).isFalse();
        assertThat(session.get("expiresIn").asLong()).isPositive();
        assertThat(session.has("refreshToken")).as("refresh token 不能出現在回應內文").isFalse();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith(AuthController.REFRESH_COOKIE + "=")
                .contains("HttpOnly").contains("Path=/api/auth").contains("SameSite=Lax");

        User user = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getPasswordHash()).isNotBlank().doesNotContain(PASSWORD);

        mvc.perform(bearer(get("/api/me"), result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    @DisplayName("註冊會寄出 6 位數驗證碼")
    void registerSendsASixDigitCode() throws Exception {
        register(EMAIL, PASSWORD);

        assertThat(lastCodeFor(EMAIL)).matches("\\d{6}");
    }

    @Test
    @DisplayName("註冊時一併記錄名稱、真實姓名與須知同意")
    void registerStoresProfileAndConsent() throws Exception {
        String version = termsProperties.currentVersion(TermsType.DONOR);
        MvcResult result = mvc.perform(withBody(post("/api/auth/register"), Map.of(
                        "email", EMAIL, "password", PASSWORD,
                        "displayName", "小明", "name", "王小明",
                        "consent", Map.of("type", "DONOR", "version", version))))
                .andExpect(status().isCreated())
                .andReturn();

        mvc.perform(bearer(get("/api/me/profile"), result))
                .andExpect(jsonPath("$.displayName").value("小明"))
                .andExpect(jsonPath("$.name").value("王小明"));
        mvc.perform(bearer(get("/api/me/terms"), result))
                .andExpect(jsonPath("$[?(@.type=='DONOR')].upToDate").value(true));
    }

    @Test
    @DisplayName("沒填用戶名稱時，用信箱 @ 前面那段")
    void registerFallsBackToTheLocalPartOfTheEmail() throws Exception {
        MvcResult result = register(EMAIL, PASSWORD);

        mvc.perform(bearer(get("/api/me/profile"), result))
                .andExpect(jsonPath("$.displayName").value("flow"));
    }

    @Test
    @DisplayName("須知版本過期時註冊照樣成功，只是不記錄同意")
    void registerIgnoresAStaleTermsVersion() throws Exception {
        MvcResult result = mvc.perform(withBody(post("/api/auth/register"), Map.of(
                        "email", EMAIL, "password", PASSWORD,
                        "consent", Map.of("type", "DONOR", "version", "v0-stale"))))
                .andExpect(status().isCreated())
                .andReturn();

        mvc.perform(bearer(get("/api/me/terms"), result))
                .andExpect(jsonPath("$[?(@.type=='DONOR')].upToDate").value(false));
    }

    @Test
    @DisplayName("太短的密碼被拒絕")
    void registerRejectsAShortPassword() throws Exception {
        mvc.perform(withBody(post("/api/auth/register"), Map.of("email", EMAIL, "password", "1234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertThat(users.findByEmailIgnoreCase(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("信箱已註冊回 409，且不寄信")
    void registerRejectsAnExistingEmail() throws Exception {
        register(EMAIL, PASSWORD);

        mvc.perform(withBody(post("/api/auth/register"), Map.of("email", EMAIL, "password", "another-pass-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));
        verify(mail, times(1)).sendVerificationCode(eq(EMAIL), org.mockito.ArgumentMatchers.anyString());
    }

    // ------------------------------------------------------------ 信箱驗證碼

    @Test
    @DisplayName("輸入正確的驗證碼之後，信箱立即視為已驗證")
    void correctCodeVerifiesTheEmail() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);

        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", lastCodeFor(EMAIL))), session))
                .andExpect(status().isNoContent());

        // 同一張 access token，不必重新登入
        mvc.perform(bearer(get("/api/me"), session))
                .andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("驗證碼錯誤會告知剩餘次數")
    void wrongCodeReportsTheRemainingAttempts() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);
        String wrong = wrongCodeFor(lastCodeFor(EMAIL));

        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", wrong)), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CODE_INVALID"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("4")));
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("同一組驗證碼錯 5 次就作廢，之後連正確的碼也不能用")
    void tooManyWrongGuessesLockTheCode() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);
        String real = lastCodeFor(EMAIL);
        String wrong = wrongCodeFor(real);

        for (int i = 0; i < 4; i++) {
            mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", wrong)), session))
                    .andExpect(jsonPath("$.errorCode").value("CODE_INVALID"));
        }
        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", wrong)), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CODE_LOCKED"));

        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", real)), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CODE_LOCKED"));
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("過期的驗證碼不能用")
    void expiredCodeIsRejected() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);
        String code = lastCodeFor(EMAIL);
        syncDatabase(() -> jdbc.update("UPDATE auth_tokens SET expires_at = now() - interval '1 minute'"));

        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", code)), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CODE_EXPIRED"));
    }

    @Test
    @DisplayName("驗證碼只能是 6 位數字")
    void codeMustBeSixDigits() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);

        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", "12ab56")), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("別人的驗證碼不能拿來驗證自己的信箱")
    void aCodeIsBoundToItsOwner() throws Exception {
        MvcResult alice = register("alice@example.com", PASSWORD);
        register("bob@example.com", PASSWORD);
        String bobsCode = lastCodeFor("bob@example.com");
        String alicesCode = lastCodeFor("alice@example.com");
        // 兩個人偶然拿到同一組碼的機率很低，但不是零——真的撞到就換一組，測的才是「綁定」
        if (bobsCode.equals(alicesCode)) {
            return;
        }

        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", bobsCode)), alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CODE_INVALID"));
        assertThat(users.findByEmailIgnoreCase("alice@example.com").orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("資料庫裡存的不是驗證碼明文")
    void theCodeIsNotStoredInPlainText() throws Exception {
        register(EMAIL, PASSWORD);
        String code = lastCodeFor(EMAIL);
        em.flush();

        List<String> hashes = jdbc.queryForList("SELECT token_hash FROM auth_tokens", String.class);
        assertThat(hashes).hasSize(1);
        assertThat(hashes.get(0)).isNotEqualTo(code).hasSize(64);
    }

    @Test
    @DisplayName("重寄：太頻繁回 429，隔夠久之後舊碼作廢、新碼可用")
    void resendRotatesTheCodeAndIsRateLimited() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);
        String first = lastCodeFor(EMAIL);

        mvc.perform(bearer(post("/api/auth/verification/resend"), session))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));

        syncDatabase(() -> jdbc.update("UPDATE auth_tokens SET created_at = created_at - interval '2 minutes'"));
        mvc.perform(bearer(post("/api/auth/verification/resend"), session))
                .andExpect(status().isNoContent());
        String second = lastCodeFor(EMAIL);

        if (!first.equals(second)) {
            mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", first)), session))
                    .andExpect(jsonPath("$.errorCode").value("CODE_INVALID"));
        }
        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", second)), session))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("重寄一天最多 5 封")
    void resendIsCappedPerDay() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);

        // 註冊那封算第 1 封，再重寄 4 次湊滿 5 封
        for (int i = 0; i < 4; i++) {
            syncDatabase(() -> jdbc.update("UPDATE auth_tokens SET created_at = created_at - interval '2 minutes'"));
            mvc.perform(bearer(post("/api/auth/verification/resend"), session))
                    .andExpect(status().isNoContent());
        }

        syncDatabase(() -> jdbc.update("UPDATE auth_tokens SET created_at = created_at - interval '2 minutes'"));
        mvc.perform(bearer(post("/api/auth/verification/resend"), session))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("已驗證的帳號重寄不會再寄信")
    void resendDoesNothingOnceVerified() throws Exception {
        MvcResult session = register(EMAIL, PASSWORD);
        mvc.perform(bearer(withBody(post("/api/auth/verify-email"), Map.of("code", lastCodeFor(EMAIL))), session))
                .andExpect(status().isNoContent());

        mvc.perform(bearer(post("/api/auth/verification/resend"), session))
                .andExpect(status().isNoContent());
        verify(mail, times(1)).sendVerificationCode(eq(EMAIL), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("交易 commit 之後才真的寄出驗證碼信")
    void theMailIsSentOnlyAfterCommit() throws Exception {
        try {
            register(EMAIL, PASSWORD);
            assertThat(((RecordingMailSender) mailSender).sent()).as("commit 之前還不能寄").isEmpty();

            TestTransaction.flagForCommit();
            TestTransaction.end();

            long deadline = System.currentTimeMillis() + 3000;
            while (((RecordingMailSender) mailSender).sent().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            var sent = ((RecordingMailSender) mailSender).sent();
            assertThat(sent).hasSize(1);
            assertThat(sent.get(0).getTo()).containsExactly(EMAIL);
            assertThat(sent.get(0).getText()).contains(lastCodeFor(EMAIL));
        } finally {
            if (TestTransaction.isActive()) {
                TestTransaction.end();
            }
            TestTransaction.start();
            jdbc.update("DELETE FROM users WHERE lower(email) = lower(?)", EMAIL);
            TestTransaction.flagForCommit();
            TestTransaction.end();
            TestTransaction.start();
        }
    }

    // ------------------------------------------------------------ 登入

    @Test
    @DisplayName("用正確的密碼登入")
    void loginWithTheCorrectPassword() throws Exception {
        register(EMAIL, PASSWORD);

        MvcResult result = login(EMAIL.toUpperCase(), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(sessionCookie(result)).isNotNull();
        mvc.perform(bearer(get("/api/me"), result)).andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("密碼錯誤與帳號不存在回一樣的訊息")
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        register(EMAIL, PASSWORD);

        MvcResult wrongPassword = login(EMAIL, "wrong-password-1");
        MvcResult unknownEmail = login("nobody@example.com", "wrong-password-1");

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(wrongPassword).get("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body(unknownEmail).get("detail")).isEqualTo(body(wrongPassword).get("detail"));
    }

    @Test
    @DisplayName("連續輸錯 5 次鎖定帳號，鎖定期間連正確的密碼也不能登入")
    void repeatedFailuresLockTheAccount() throws Exception {
        register(EMAIL, PASSWORD);

        for (int i = 0; i < 4; i++) {
            assertThat(login(EMAIL, "wrong-password-1").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult fifth = login(EMAIL, "wrong-password-1");
        assertThat(fifth.getResponse().getStatus()).isEqualTo(423);
        assertThat(body(fifth).get("errorCode").asText()).isEqualTo("ACCOUNT_LOCKED");

        assertThat(login(EMAIL, PASSWORD).getResponse().getStatus()).isEqualTo(423);

        // 鎖定到期之後恢復正常
        syncDatabase(() -> jdbc.update("UPDATE users SET locked_until = now() - interval '1 minute'"));
        assertThat(login(EMAIL, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("登入成功會清掉先前累計的失敗次數")
    void successfulLoginResetsTheFailureCount() throws Exception {
        register(EMAIL, PASSWORD);
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong-password-1");
        }
        assertThat(login(EMAIL, PASSWORD).getResponse().getStatus()).isEqualTo(200);

        for (int i = 0; i < 4; i++) {
            assertThat(login(EMAIL, "wrong-password-1").getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("被停用的帳號登不進來")
    void disabledAccountCannotLogIn() throws Exception {
        register(EMAIL, PASSWORD);
        syncDatabase(() -> jdbc.update("UPDATE users SET disabled = true"));

        MvcResult result = login(EMAIL, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).get("errorCode").asText()).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    @DisplayName("沒有密碼的帳號（例如開發模式建立的）不能用密碼登入")
    void accountWithoutAPasswordCannotLogIn() throws Exception {
        users.save(User.newDonor("nopass@example.com", "沒設密碼"));

        assertThat(login("nopass@example.com", "anything-goes-1").getResponse().getStatus()).isEqualTo(401);
    }

    // ------------------------------------------------------------ 登入狀態

    @Test
    @DisplayName("用 cookie 換發新的 access token，同時輪替 cookie")
    void refreshRotatesTheCookie() throws Exception {
        Cookie original = sessionCookie(register(EMAIL, PASSWORD));

        MvcResult refreshed = mvc.perform(refreshWith(original)).andExpect(status().isOk()).andReturn();

        Cookie rotated = sessionCookie(refreshed);
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());
        mvc.perform(bearer(get("/api/me"), refreshed)).andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("沒有 cookie 或 cookie 不認得，換發回 401")
    void refreshWithoutAValidCookieIsRejected() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(refreshWith(new Cookie(AuthController.REFRESH_COOKIE, "garbage")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("已撤銷的 cookie 又被使用：整串登入狀態一併撤銷")
    void reusingARevokedCookieRevokesTheWholeFamily() throws Exception {
        Cookie original = sessionCookie(register(EMAIL, PASSWORD));
        Cookie rotated = sessionCookie(mvc.perform(refreshWith(original)).andReturn());

        // 超過兩個分頁同時換發的寬限時間，仍然拿舊的來用，就是 cookie 被複製了
        syncDatabase(() -> jdbc.update("UPDATE refresh_tokens SET revoked_at = now() - interval '1 minute' "
                + "WHERE revoked_at IS NOT NULL"));
        mvc.perform(refreshWith(original)).andExpect(status().isUnauthorized());

        // 本人手上最新的那張也一起失效
        mvc.perform(refreshWith(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("剛輪替完又被用舊 cookie 換發（兩個分頁同時換），不波及新的那張")
    void aRaceBetweenTwoTabsDoesNotLogTheUserOut() throws Exception {
        Cookie original = sessionCookie(register(EMAIL, PASSWORD));
        Cookie rotated = sessionCookie(mvc.perform(refreshWith(original)).andReturn());

        mvc.perform(refreshWith(original)).andExpect(status().isUnauthorized());

        mvc.perform(refreshWith(rotated)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("換發與登出只接受自己前端的來源")
    void refreshAndLogoutCheckTheOrigin() throws Exception {
        Cookie cookie = sessionCookie(register(EMAIL, PASSWORD));

        // 不在白名單的來源在更前面的 CORS 層就被擋下（回應不是我們的 JSON），
        // controller 裡的 Origin 檢查是第二道
        mvc.perform(refreshWith(cookie).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());

        mvc.perform(refreshWith(cookie).header("Origin", "http://localhost:5173")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("登出後 cookie 失效，並清除 cookie")
    void logoutRevokesTheCookie() throws Exception {
        Cookie cookie = sessionCookie(register(EMAIL, PASSWORD));

        MvcResult result = mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        mvc.perform(refreshWith(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("沒登入也能呼叫登出")
    void logoutWithoutACookieIsFine() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ 重設密碼

    @Test
    @DisplayName("忘記密碼：信箱不存在也回 204，而且不寄信")
    void forgotPasswordDoesNotRevealWhetherTheEmailExists() throws Exception {
        mvc.perform(withBody(post("/api/auth/password/forgot"), Map.of("email", "nobody@example.com")))
                .andExpect(status().isNoContent());

        verify(mail, never()).sendPasswordReset(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("重設密碼：新密碼可登入、舊密碼失效、所有登入狀態被撤銷、信箱視為已驗證")
    void resetPasswordFlow() throws Exception {
        Cookie signedIn = sessionCookie(register(EMAIL, PASSWORD));

        mvc.perform(withBody(post("/api/auth/password/forgot"), Map.of("email", EMAIL.toUpperCase())))
                .andExpect(status().isNoContent());
        String token = lastResetTokenFor(EMAIL);

        mvc.perform(withBody(post("/api/auth/password/reset"),
                        Map.of("token", token, "newPassword", "brand-new-pass-9")))
                .andExpect(status().isNoContent());

        assertThat(login(EMAIL, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(EMAIL, "brand-new-pass-9").getResponse().getStatus()).isEqualTo(200);
        mvc.perform(refreshWith(signedIn)).andExpect(status().isUnauthorized());
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("重設連結只能用一次")
    void resetTokenIsSingleUse() throws Exception {
        register(EMAIL, PASSWORD);
        mvc.perform(withBody(post("/api/auth/password/forgot"), Map.of("email", EMAIL)));
        String token = lastResetTokenFor(EMAIL);
        Map<String, String> reset = Map.of("token", token, "newPassword", "brand-new-pass-9");

        mvc.perform(withBody(post("/api/auth/password/reset"), reset)).andExpect(status().isNoContent());

        mvc.perform(withBody(post("/api/auth/password/reset"), reset))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("過期、亂寫的重設連結不能用")
    void expiredOrBogusResetTokensAreRejected() throws Exception {
        register(EMAIL, PASSWORD);
        mvc.perform(withBody(post("/api/auth/password/forgot"), Map.of("email", EMAIL)));
        String token = lastResetTokenFor(EMAIL);
        syncDatabase(() -> jdbc.update("UPDATE auth_tokens SET expires_at = now() - interval '1 minute'"));

        mvc.perform(withBody(post("/api/auth/password/reset"),
                        Map.of("token", token, "newPassword", "brand-new-pass-9")))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));
        mvc.perform(withBody(post("/api/auth/password/reset"),
                        Map.of("token", "bogus", "newPassword", "brand-new-pass-9")))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));
        assertThat(login(EMAIL, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("重設密碼也要符合密碼長度")
    void resetRejectsAShortPassword() throws Exception {
        register(EMAIL, PASSWORD);
        mvc.perform(withBody(post("/api/auth/password/forgot"), Map.of("email", EMAIL)));

        mvc.perform(withBody(post("/api/auth/password/reset"),
                        Map.of("token", lastResetTokenFor(EMAIL), "newPassword", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("連續申請重設密碼，只寄一封（限流是靜默的，不回 429）")
    void forgotPasswordIsSilentlyRateLimited() throws Exception {
        register(EMAIL, PASSWORD);

        for (int i = 0; i < 3; i++) {
            mvc.perform(withBody(post("/api/auth/password/forgot"), Map.of("email", EMAIL)))
                    .andExpect(status().isNoContent());
        }

        verify(mail, times(1)).sendPasswordReset(eq(EMAIL), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("重新申請之後，先前的重設連結作廢")
    void aNewResetRequestInvalidatesTheOldLink() throws Exception {
        register(EMAIL, PASSWORD);
        mvc.perform(withBody(post("/api/auth/password/forgot"), Map.of("email", EMAIL)));
        String first = lastResetTokenFor(EMAIL);
        syncDatabase(() -> jdbc.update("UPDATE auth_tokens SET created_at = created_at - interval '2 minutes'"));
        mvc.perform(withBody(post("/api/auth/password/forgot"), Map.of("email", EMAIL)));

        mvc.perform(withBody(post("/api/auth/password/reset"),
                        Map.of("token", first, "newPassword", "brand-new-pass-9")))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));
        mvc.perform(withBody(post("/api/auth/password/reset"),
                        Map.of("token", lastResetTokenFor(EMAIL), "newPassword", "brand-new-pass-9")))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ 清理

    @Test
    @DisplayName("清理排程只刪過期很久的 token")
    void cleanupDeletesOnlyStaleTokens() throws Exception {
        register(EMAIL, PASSWORD);
        em.flush();
        jdbc.update("INSERT INTO auth_tokens (user_id, purpose, token_hash, expires_at) "
                + "SELECT id, 'RESET_PASSWORD', 'stale', now() - interval '30 days' FROM users");

        int[] deleted = localAuth.cleanupStaleTokens();

        assertThat(deleted[0]).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens", Integer.class))
                .as("剛寄出的驗證碼還在").isEqualTo(1);
    }

}
