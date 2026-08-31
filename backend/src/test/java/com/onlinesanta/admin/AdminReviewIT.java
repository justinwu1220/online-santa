package com.onlinesanta.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.admin.dto.ReviewDecisionRequest;
import com.onlinesanta.organization.dto.OrganizationRegistrationRequest;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.dto.WishRequest;

@DisplayName("機構審核後台")
class AdminReviewIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "applicant@example.org";
    private static final String DONOR = "bystander@example.com";

    private UUID registerOrganization(String name, String memberEmail) throws Exception {
        var request = new OrganizationRegistrationRequest(
                name, "王承辦", "contact@example.org", "02-1234-5678", "台北市", "服務失依兒童");
        String body = mvc.perform(as(withBody(post("/api/organizations"), request), memberEmail))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private WishRequest wishRequest() {
        return new WishRequest("小星", AgeRange.AGE_7_9, "畫畫",
                "色鉛筆", "描述", WishCategory.ART, PriceRange.UNDER_500);
    }

    // ------------------------------------------------------------ 權限

    @Test
    @DisplayName("非管理員看不到審核後台")
    void nonAdminsCannotReachTheConsole() throws Exception {
        registerOrganization("申請中之家", ORG_USER);

        mvc.perform(as(get("/api/admin/organizations"), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mvc.perform(as(get("/api/admin/organizations"), ORG_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未登入看不到審核後台")
    void anonymousCannotReachTheConsole() throws Exception {
        mvc.perform(get("/api/admin/organizations"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ 審核流程

    @Test
    @DisplayName("待審清單列出剛註冊的機構")
    void listsPendingOrganizations() throws Exception {
        registerOrganization("待審核之家", ORG_USER);

        mvc.perform(as(get("/api/admin/organizations").param("status", "PENDING"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("待審核之家"))
                .andExpect(jsonPath("$.content[0].contactEmail").value("contact@example.org"));
    }

    @Test
    @DisplayName("核准後機構才能上架願望；核准前只能存草稿")
    void approvalUnlocksWishPublishing() throws Exception {
        UUID orgId = registerOrganization("待核准之家", ORG_USER);

        // 核准前：草稿可以建立——讓機構在等待期間先準備內容
        String body = mvc.perform(as(withBody(post("/api/wishes"), wishRequest()), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        UUID draft = UUID.fromString(json.readTree(body).get("id").asText());

        // 但公開被擋下——把關在這一步
        mvc.perform(as(post("/api/wishes/{id}/publish", draft), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_APPROVED"));

        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/approve", orgId),
                        new ReviewDecisionRequest("證件齊全")), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewNote").value("證件齊全"))
                .andExpect(jsonPath("$.reviewedAt").exists())
                .andExpect(jsonPath("$.reviewedBy").exists());

        // 核准後立刻生效——不必等 token 重簽，因為權限是查資料庫而非讀 claims。
        // 而且審核期間存的那份草稿現在可以直接上架，不必重打一次
        mvc.perform(as(post("/api/wishes/{id}/publish", draft), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("退件會附上原因，機構補件後可重新送審")
    void rejectionCarriesAReasonAndAllowsResubmission() throws Exception {
        UUID orgId = registerOrganization("被退件之家", ORG_USER);

        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/reject", orgId),
                        new ReviewDecisionRequest("請補立案證明")), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("請補立案證明"));

        // 機構看得到退件原因
        mvc.perform(as(get("/api/organizations/me"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("請補立案證明"))
                .andExpect(jsonPath("$.canPublishWishes").value(false));

        mvc.perform(as(post("/api/organizations/me/resubmit"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 重新送審後又出現在待審清單
        mvc.perform(as(get("/api/admin/organizations").param("status", "PENDING"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("重複裁決會被擋下")
    void cannotDecideTwice() throws Exception {
        UUID orgId = registerOrganization("重複審核之家", ORG_USER);

        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/approve", orgId),
                new ReviewDecisionRequest("第一次")), ADMIN)).andExpect(status().isOk());

        // 管理員在清單頁按了兩次，第二次要給明確錯誤而非靜默覆寫審核紀錄
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/approve", orgId),
                        new ReviewDecisionRequest("第二次")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_PENDING"));
    }

    @Test
    @DisplayName("尚未被退件的機構不能重新送審")
    void resubmissionRequiresAPriorRejection() throws Exception {
        registerOrganization("還在等的機構", ORG_USER);

        mvc.perform(as(post("/api/organizations/me/resubmit"), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_REJECTED"));
    }

    @Test
    @DisplayName("管理員不能自己註冊機構")
    void adminsCannotRegisterOrganizations() throws Exception {
        // 電話與地址填滿：這條測試要驗的是「管理員被擋下」，欄位缺漏會先回 400，
        // 授權那一條根本不會被執行到，測試就會因為錯誤的理由而通過
        var request = new OrganizationRegistrationRequest(
                "球員兼裁判之家", "王承辦", "admin@example.org", "02-1234-5678", "台北市某路 1 號", null);

        mvc.perform(as(withBody(post("/api/organizations"), request), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_CANNOT_REGISTER_ORG"));
    }
}
