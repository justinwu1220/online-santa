package com.onlinesanta.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.user.dto.UserProfileUpdateRequest;

class MeProfileApiIT extends ApiIntegrationTest {

    private static final String USER = "donor@example.com";

    @Test
    void getsOwnProfile() throws Exception {
        // 測試輔助方法為這個信箱建立帳號（displayName 預設是信箱），此時還沒有人填過其他資料
        mvc.perform(as(get("/api/me"), USER)).andExpect(status().isOk());

        mvc.perform(as(get("/api/me/profile"), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(USER))
                // 這個端點照實顯示資料庫裡的值，不自己加工
                .andExpect(jsonPath("$.displayName").value(USER))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void newAccountHasNotSeenTheOnboardingUntilItIsMarked() throws Exception {
        mvc.perform(as(get("/api/me"), USER)).andExpect(status().isOk());

        mvc.perform(as(get("/api/me/profile"), USER))
                .andExpect(jsonPath("$.onboarded").value(false));

        mvc.perform(as(post("/api/me/onboarded"), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarded").value(true));

        // 冪等：看過的人再呼叫一次不出錯，狀態維持已看過
        mvc.perform(as(post("/api/me/onboarded"), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarded").value(true));
        mvc.perform(as(get("/api/me/profile"), USER))
                .andExpect(jsonPath("$.onboarded").value(true));
    }

    @Test
    void onboardingRequiresLogin() throws Exception {
        mvc.perform(post("/api/me/onboarded")).andExpect(status().isUnauthorized());
    }

    @Test
    void updatesDisplayNameAndPhone() throws Exception {
        mvc.perform(as(get("/api/me"), USER)).andExpect(status().isOk());

        var update = new UserProfileUpdateRequest("王小明", "王大明", "0912-345-678");

        mvc.perform(as(withBody(patch("/api/me/profile"), update), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("王小明"))
                .andExpect(jsonPath("$.name").value("王大明"))
                .andExpect(jsonPath("$.phone").value("0912-345-678"));

        mvc.perform(as(get("/api/me/profile"), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("王小明"))
                .andExpect(jsonPath("$.name").value("王大明"))
                .andExpect(jsonPath("$.phone").value("0912-345-678"));
    }

    @Test
    void rejectsBlankDisplayName() throws Exception {
        mvc.perform(as(get("/api/me"), USER)).andExpect(status().isOk());

        var invalid = new UserProfileUpdateRequest("   ", null, null);

        mvc.perform(as(withBody(patch("/api/me/profile"), invalid), USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());
    }

    @Test
    void allowsUnverifiedEmailToEditOwnProfile() throws Exception {
        mvc.perform(asUnverified(get("/api/me"), "unverified@example.com"))
                .andExpect(status().isOk());

        var update = new UserProfileUpdateRequest("未驗證的人", null, null);

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
