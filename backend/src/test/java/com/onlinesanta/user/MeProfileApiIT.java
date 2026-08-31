package com.onlinesanta.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.user.dto.UserProfileUpdateRequest;

class MeProfileApiIT extends ApiIntegrationTest {

    private static final String USER = "donor@example.com";

    @Test
    void getsOwnProfileWithJitFallbackDisplayName() throws Exception {
        // 觸發 JIT provisioning，此時還沒有人填過 displayName
        mvc.perform(as(get("/api/me"), USER)).andExpect(status().isOk());

        mvc.perform(as(get("/api/me/profile"), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(USER))
                // JIT 建立時沒有 displayName 會 fallback 成 email——這裡照實顯示，
                // 不是這個端點自己做的事
                .andExpect(jsonPath("$.displayName").value(USER))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void updatesDisplayNameAndPhone() throws Exception {
        mvc.perform(as(get("/api/me"), USER)).andExpect(status().isOk());

        var update = new UserProfileUpdateRequest("王小明", "0912-345-678");

        mvc.perform(as(withBody(patch("/api/me/profile"), update), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("王小明"))
                .andExpect(jsonPath("$.phone").value("0912-345-678"));

        mvc.perform(as(get("/api/me/profile"), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("王小明"))
                .andExpect(jsonPath("$.phone").value("0912-345-678"));
    }

    @Test
    void rejectsBlankDisplayName() throws Exception {
        mvc.perform(as(get("/api/me"), USER)).andExpect(status().isOk());

        var invalid = new UserProfileUpdateRequest("   ", null);

        mvc.perform(as(withBody(patch("/api/me/profile"), invalid), USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());
    }

    @Test
    void allowsUnverifiedEmailToEditOwnProfile() throws Exception {
        mvc.perform(asUnverified(get("/api/me"), "unverified@example.com"))
                .andExpect(status().isOk());

        var update = new UserProfileUpdateRequest("未驗證的人", null);

        mvc.perform(asUnverified(withBody(patch("/api/me/profile"), update),
                        "unverified@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("未驗證的人"));
    }

    @Test
    void deniesAnonymousAccess() throws Exception {
        mvc.perform(get("/api/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }
}
