package com.onlinesanta.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.organization.dto.OrganizationRegistrationRequest;
import com.onlinesanta.organization.dto.OrganizationUpdateRequest;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;

class OrganizationApiIT extends ApiIntegrationTest {

    private static final String ORG_USER = "org@example.org";

    @Autowired
    UserRepository users;

    private OrganizationRegistrationRequest registration(String name) {
        return new OrganizationRegistrationRequest(
                name, "contact@example.org", "02-1234-5678",
                "台北市中正區某路 1 號", "服務失依兒童的機構");
    }

    @Test
    void registersOrganizationAsPendingAndPromotesRegistrant() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("陽光之家")), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("陽光之家"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.canPublishWishes").value(false))
                // 待審核就能存草稿，只是不能公開——後台靠這兩個旗標決定按鈕的狀態
                .andExpect(jsonPath("$.canDraftWishes").value(true))
                .andExpect(jsonPath("$.releasePolicy").value("MANUAL"))
                .andExpect(jsonPath("$.releaseAfterDays").doesNotExist());

        var registrant = users.findByEmailIgnoreCase(ORG_USER).orElseThrow();
        assertThat(registrant.getRole()).isEqualTo(UserRole.ORG_MEMBER);
        assertThat(registrant.getOrganizationId()).isNotNull();
    }

    @Test
    void rejectsAnonymousRegistration() throws Exception {
        mvc.perform(withBody(post("/api/organizations"), registration("無名氏之家")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsDuplicateOrganizationName() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("重複之家")), ORG_USER))
                .andExpect(status().isCreated());

        mvc.perform(as(withBody(post("/api/organizations"), registration("重複之家")),
                        "another@example.org"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NAME_TAKEN"));
    }

    @Test
    void rejectsSecondRegistrationBySameUser() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("第一家")), ORG_USER))
                .andExpect(status().isCreated());

        mvc.perform(as(withBody(post("/api/organizations"), registration("第二家")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_ORGANIZATION"));
    }

    @Test
    void reportsFieldLevelValidationErrors() throws Exception {
        var invalid = new OrganizationRegistrationRequest("", "not-an-email", null, null, null);

        mvc.perform(as(withBody(post("/api/organizations"), invalid), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.contactEmail").exists())
                // 電話與地址是必填：捐贈者認領後就是照著這兩個欄位把禮物寄出去的
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists());
    }

    @Test
    void rejectsBlankPhoneAndAddressOnUpdateToo() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("先建立起來")), ORG_USER))
                .andExpect(status().isCreated());

        // 只在註冊時擋是不夠的——機構之後在設定頁清空一樣會讓寄送地址消失
        var cleared = new OrganizationUpdateRequest(
                "先建立起來", "contact@example.org", "   ", "", null, ReleasePolicy.MANUAL, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), cleared), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists());
    }

    @Test
    void deniesOrganizationEndpointsToNonMembers() throws Exception {
        mvc.perform(as(get("/api/organizations/me"), "donor@example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    @Test
    void updatesReleasePolicyToAutomatic() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("自動釋回之家")), ORG_USER))
                .andExpect(status().isCreated());

        var update = new OrganizationUpdateRequest(
                "自動釋回之家", "new@example.org", "02-0000-0000",
                "新地址", "新簡介", ReleasePolicy.AUTO, 10);

        mvc.perform(as(withBody(patch("/api/organizations/me"), update), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasePolicy").value("AUTO"))
                .andExpect(jsonPath("$.releaseAfterDays").value(10))
                .andExpect(jsonPath("$.contactEmail").value("new@example.org"));
    }

    @Test
    void clearsGracePeriodWhenSwitchingBackToManual() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("切回手動之家")), ORG_USER))
                .andExpect(status().isCreated());

        var toAuto = new OrganizationUpdateRequest("切回手動之家", "a@example.org",
                "02-1234-5678", "台北市中正區某路 1 號", null, ReleasePolicy.AUTO, 5);
        mvc.perform(as(withBody(patch("/api/organizations/me"), toAuto), ORG_USER))
                .andExpect(status().isOk());

        // 切回 MANUAL 時必須清掉天數，否則違反 ck_org_release_after_days
        var toManual = new OrganizationUpdateRequest("切回手動之家", "a@example.org",
                "02-1234-5678", "台北市中正區某路 1 號", null, ReleasePolicy.MANUAL, 5);
        mvc.perform(as(withBody(patch("/api/organizations/me"), toManual), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasePolicy").value("MANUAL"))
                .andExpect(jsonPath("$.releaseAfterDays").doesNotExist());
    }

    @Test
    void rejectsAutomaticPolicyWithoutGracePeriod() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("缺天數之家")), ORG_USER))
                .andExpect(status().isCreated());

        var invalid = new OrganizationUpdateRequest("缺天數之家", "a@example.org",
                "02-1234-5678", "台北市中正區某路 1 號", null, ReleasePolicy.AUTO, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), invalid), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RELEASE_POLICY"));
    }
}
