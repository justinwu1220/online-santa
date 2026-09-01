package com.onlinesanta.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

@DisplayName("JIT 建立帳號的 displayName 決定")
class DisplayNameProvisioningIT extends ApiIntegrationTest {

    @Autowired
    UserRepository users;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            withToken(String token) {
        return get("/api/me").header("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("token 沒有 name claim 時，新帳號用信箱 @ 前面那段")
    void jitFallsBackToEmailLocalPartWhenTokenHasNoName() throws Exception {
        String email = "no-name-claim@example.com";
        String token = TestJwtSupport.tokenFor(email, null);

        mvc.perform(withToken(token)).andExpect(status().isOk());

        var created = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(created.getDisplayName()).isEqualTo("no-name-claim");
    }

    @Test
    @DisplayName("token 有 name claim 時，新帳號直接採用")
    void jitUsesNameClaimWhenPresent() throws Exception {
        String email = "has-name-claim@example.com";
        String token = TestJwtSupport.tokenFor(email, "王小明");

        mvc.perform(withToken(token)).andExpect(status().isOk());

        var created = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(created.getDisplayName()).isEqualTo("王小明");
    }

    @Test
    @DisplayName("既有帳號的 displayName 是舊 fallback（等於 email）時，下次登入會被 name claim 修正")
    void repairsLegacyFallbackDisplayNameOnNextLogin() throws Exception {
        String email = "legacy-fallback@example.com";
        users.save(User.newDonor(TestJwtSupport.uidFor(email), email, email));

        String token = TestJwtSupport.tokenFor(email, "小明真的叫小明");
        mvc.perform(withToken(token)).andExpect(status().isOk());

        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("小明真的叫小明");
    }

    @Test
    @DisplayName("使用者自訂過的 displayName 不會被 token 的 name claim 覆蓋")
    void neverOverwritesACustomDisplayName() throws Exception {
        String email = "custom-name@example.com";
        users.save(User.newDonor(TestJwtSupport.uidFor(email), email, "使用者自己取的名字"));

        String token = TestJwtSupport.tokenFor(email, "Google 帳號上的名字");
        mvc.perform(withToken(token)).andExpect(status().isOk());

        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("使用者自己取的名字");
    }
}
