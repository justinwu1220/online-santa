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
 * 管理員暫時凍結願望（設計稿 16.3）：凍結後願望牆與詳情頁都看不到，也不能被認領；
 * 機構後台仍看得到；已認領的願望，認領者仍看得到自己的認領（標示暫停處理中），寄送期限暫停；解除後恢復。
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

    @Autowired
    com.onlinesanta.claim.ClaimRepository claimRepository;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    jakarta.persistence.EntityManager em;

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

    private UUID claimedWish(String title) throws Exception {
        UUID wishId = publishedWish(title);
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private UUID wishIdOf(UUID claimId) {
        return jdbc.queryForObject("select wish_id from claims where id = ?", UUID.class, claimId);
    }

    @Test
    @DisplayName("已被認領的願望凍結後，認領者仍看得到自己的認領並標示暫停處理中，機構也看得到")
    void frozenClaimedWishStaysVisibleToDonorAsPaused() throws Exception {
        UUID claimId = claimedWish("認領後被凍結");
        UUID wishId = wishIdOf(claimId);

        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.paused").value(false));

        freeze(wishId, "調查中，先暫停");

        mvc.perform(as(get("/api/claims/me"), DONOR)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paused").value(true));
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true));
        mvc.perform(as(get("/api/claims/{id}/messages", claimId), DONOR)).andExpect(status().isOk());

        // 公開的願望牆與詳情仍然看不到
        mvc.perform(get("/api/wishes/{id}", wishId)).andExpect(status().isNotFound());
        // 機構後台仍保留
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(status().isOk());

        mvc.perform(as(post("/api/admin/wishes/{id}/unfreeze", wishId), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false));
    }

    @Test
    @DisplayName("凍結期間認領者不能回報寄送或取消；解除後恢復")
    void frozenClaimCannotShipOrCancel() throws Exception {
        UUID claimId = claimedWish("凍結中不能寄");
        UUID wishId = wishIdOf(claimId);
        freeze(wishId, "調查中，先暫停");

        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        com.onlinesanta.support.ShipRequests.of("郵局", "R1")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_FROZEN"));
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claimId),
                        java.util.Map.of("reason", "不想送了")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_FROZEN"));

        mvc.perform(as(post("/api/admin/wishes/{id}/unfreeze", wishId), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claimId),
                        java.util.Map.of("reason", "不想送了")), DONOR))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("凍結期間寄送期限暫停：不算逾期、不會被自動收回；解除後暫停的時長加回期限")
    void freezePausesTheShipDeadline() throws Exception {
        UUID claimId = claimedWish("期限暫停");
        UUID wishId = wishIdOf(claimId);
        // 測試在同一個交易裡：先寫出再用 SQL 改期限，並清掉持久化脈絡，之後才讀得到新值
        em.flush();
        jdbc.update("update claims set ship_deadline_at = now() - interval '1 day' where id = ?", claimId);
        em.clear();
        assertThat(claimRepository.findOverdue(java.time.Instant.now())).hasSize(1);
        java.time.Instant before = claimRepository.findById(claimId).orElseThrow().getShipDeadlineAt();

        freeze(wishId, "調查中，先暫停");
        em.flush();
        em.clear();

        assertThat(claimRepository.findById(claimId).orElseThrow().getDeadlinePausedAt()).isNotNull();
        assertThat(claimRepository.findOverdue(java.time.Instant.now())).isEmpty();
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.overdue").value(false));

        Thread.sleep(50);
        mvc.perform(as(post("/api/admin/wishes/{id}/unfreeze", wishId), ADMIN)).andExpect(status().isOk());
        em.flush();
        em.clear();

        var resumed = claimRepository.findById(claimId).orElseThrow();
        assertThat(resumed.getDeadlinePausedAt()).isNull();
        assertThat(resumed.getShipDeadlineAt()).isAfter(before.plusMillis(40));
        assertThat(claimRepository.findOverdue(java.time.Instant.now())).hasSize(1);
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
