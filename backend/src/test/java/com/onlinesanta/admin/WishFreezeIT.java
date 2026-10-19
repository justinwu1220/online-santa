package com.onlinesanta.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.admin.dto.WishFreezeRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

/**
 * 管理員暫時凍結願望（設計稿 16.3）：凍結後願望牆、詳情頁、認領者的「我的認領」都看不到，
 * 也不能被認領；機構後台仍看得到；解除後恢復。
 */
@DisplayName("願望凍結")
class WishFreezeIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "freeze-org@example.org";
    private static final String DONOR = "freeze-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    AdminAuditLogRepository auditLogs;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("凍結測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private UUID publishedWish(String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }

    private void freeze(UUID wishId, String note) throws Exception {
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId), new WishFreezeRequest(note)), ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("凍結後從願望牆與詳情頁消失，解除後恢復")
    void frozenWishDisappearsFromWallAndDetail() throws Exception {
        UUID wishId = publishedWish("會被凍結");
        mvc.perform(get("/api/wishes")).andExpect(jsonPath("$.totalElements").value(1));

        freeze(wishId, "疑似信件含真實姓名");

        mvc.perform(get("/api/wishes")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/wishes/{id}", wishId)).andExpect(status().isNotFound());

        mvc.perform(as(post("/api/admin/wishes/{id}/unfreeze", wishId), ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/api/wishes")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/wishes/{id}", wishId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("凍結的願望不能被認領")
    void frozenWishCannotBeClaimed() throws Exception {
        UUID wishId = publishedWish("不能認領");
        freeze(wishId, "調查中，先不要被認領");

        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("已被認領的願望凍結後，認領者的「我的認領」與認領頁一併消失，機構仍看得到")
    void frozenClaimedWishHidesFromDonorButNotOrganization() throws Exception {
        UUID wishId = publishedWish("認領後被凍結");
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(body).get("id").asText());

        mvc.perform(as(get("/api/claims/me"), DONOR)).andExpect(jsonPath("$.totalElements").value(1));

        freeze(wishId, "調查中，先暫停");

        mvc.perform(as(get("/api/claims/me"), DONOR)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/claims/{id}/messages", claimId), DONOR)).andExpect(status().isNotFound());

        // 機構後台仍保留
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(status().isOk());

        mvc.perform(as(post("/api/admin/wishes/{id}/unfreeze", wishId), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("凍結要寫稽核、通知機構，原因必填，不能重複凍結或解除沒凍結的願望")
    void freezeRulesAuditAndNotification() throws Exception {
        UUID wishId = publishedWish("規則檢查");

        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId), new WishFreezeRequest("  ")), ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/admin/wishes/{id}/unfreeze", wishId), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_FROZEN"));

        freeze(wishId, "第一次凍結");
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId), new WishFreezeRequest("第二次凍結")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_ALREADY_FROZEN"));

        assertThat(auditLogs.findAll())
                .extracting(log -> log.getAction().name())
                .contains("FREEZE_WISH");

        // 機構在該願望的對話看得到凍結的系統訊息與原因
        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value(org.hamcrest.Matchers.containsString("第一次凍結")));
    }

    @Test
    @DisplayName("還沒公開的願望不能凍結；非管理員不能凍結")
    void draftsCannotBeFrozenAndOnlyAdminsMay() throws Exception {
        Wish draft = wishes.save(Wish.draft(organization, "小月", AgeRange.AGE_4_6, null,
                "草稿", null, null, null));
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", draft.getId()), new WishFreezeRequest("不該成功")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_FREEZABLE"));

        UUID wishId = publishedWish("權限檢查");
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId), new WishFreezeRequest("我不是管理員")), DONOR))
                .andExpect(status().isForbidden());
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId), new WishFreezeRequest("我是機構")), ORG_USER))
                .andExpect(status().isForbidden());
    }
}
