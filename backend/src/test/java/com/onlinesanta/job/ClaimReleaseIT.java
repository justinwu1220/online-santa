package com.onlinesanta.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.onlinesanta.claim.dto.ShipRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("逾期認領的釋回")
class ClaimReleaseIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String AUTO_ORG = "auto-org@example.org";
    private static final String MANUAL_ORG = "manual-org@example.org";
    private static final String DONOR = "donor@example.com";
    private static final String OTHER_DONOR = "other-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager entityManager;

    private Organization autoOrganization;
    private Organization manualOrganization;

    @BeforeEach
    void setUp() {
        autoOrganization = organizationWith("自動釋回之家", AUTO_ORG, ReleasePolicy.AUTO, 7);
        manualOrganization = organizationWith("手動釋回之家", MANUAL_ORG, ReleasePolicy.MANUAL, null);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
        users.save(User.newDonor(TestJwtSupport.uidFor(OTHER_DONOR), OTHER_DONOR, "另一位民眾"));
    }

    private Organization organizationWith(String name, String memberEmail,
                                          ReleasePolicy policy, Integer days) {
        Organization org = Organization.register(name, "contact@example.org", null, null, null);
        org.approve(null, "測試資料");
        org.updateReleasePolicy(policy, days);
        organizations.save(org);

        User member = User.newDonor(TestJwtSupport.uidFor(memberEmail), memberEmail, memberEmail);
        member.joinOrganization(org.getId());
        users.save(member);
        return org;
    }

    private UUID publishedWish(Organization organization, String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }

    private UUID claim(UUID wishId, String donorEmail) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donorEmail))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    /**
     * 把寄送期限改到過去，模擬時間流逝。
     *
     * <p>用原生 SQL 繞過了 Hibernate，因此前後必須 flush 與 clear：不清空持久化脈絡的話，
     * 後續查詢會拿到快取中那份期限還沒改的 Claim，測試就白做了。
     */
    private void makeOverdue(UUID claimId) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), claimId);
        entityManager.clear();
    }

    private String wishStatusOf(UUID wishId) {
        return jdbc.queryForObject("SELECT status FROM wishes WHERE id = ?", String.class, wishId);
    }

    private String claimStatusOf(UUID claimId) {
        return jdbc.queryForObject("SELECT status FROM claims WHERE id = ?", String.class, claimId);
    }

    private String sweep() throws Exception {
        return mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------ 兩種政策

    @Test
    @DisplayName("AUTO 政策的逾期認領會被釋回，願望回到願望牆")
    void automaticPolicyReleasesOverdueClaims() throws Exception {
        UUID wishId = publishedWish(autoOrganization, "自動釋回的願望");
        UUID claimId = claim(wishId, DONOR);
        makeOverdue(claimId);

        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueFound").value(1))
                .andExpect(jsonPath("$.autoReleased").value(1))
                .andExpect(jsonPath("$.wishesReturnedToWall").value(1))
                .andExpect(jsonPath("$.flaggedForOrganization").value(0));

        assertThat(claimStatusOf(claimId)).isEqualTo("RELEASED");
        assertThat(wishStatusOf(wishId)).isEqualTo("AVAILABLE");

        // 願望重新出現在願望牆，別人可以再認領
        mvc.perform(get("/api/wishes"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), OTHER_DONOR))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("MANUAL 政策只標記逾期，不動認領")
    void manualPolicyOnlyFlagsOverdueClaims() throws Exception {
        UUID wishId = publishedWish(manualOrganization, "手動處理的願望");
        UUID claimId = claim(wishId, DONOR);
        makeOverdue(claimId);

        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueFound").value(1))
                .andExpect(jsonPath("$.autoReleased").value(0))
                .andExpect(jsonPath("$.flaggedForOrganization").value(1));

        assertThat(claimStatusOf(claimId)).isEqualTo("CLAIMED");
        assertThat(wishStatusOf(wishId)).isEqualTo("CLAIMED");
    }

    @Test
    @DisplayName("MANUAL 機構在後台看得到逾期清單，並可一鍵收回")
    void manualOrganizationSeesOverdueListAndCanRelease() throws Exception {
        UUID wishId = publishedWish(manualOrganization, "逾期提醒");
        UUID claimId = claim(wishId, DONOR);
        makeOverdue(claimId);

        mvc.perform(as(get("/api/organizations/me/claims/overdue"), MANUAL_ORG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].overdue").value(true));

        mvc.perform(as(post("/api/organizations/me/claims/{id}/release", claimId), MANUAL_ORG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        assertThat(wishStatusOf(wishId)).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("逾期清單不會洩漏別家機構的認領")
    void overdueListIsScopedToTheOwningOrganization() throws Exception {
        makeOverdue(claim(publishedWish(manualOrganization, "手動機構的"), DONOR));

        mvc.perform(as(get("/api/organizations/me/claims/overdue"), MANUAL_ORG))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/organizations/me/claims/overdue"), AUTO_ORG))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------ 掃描的邊界

    @Test
    @DisplayName("尚未到期的認領不受影響")
    void claimsWithinTheDeadlineAreUntouched() throws Exception {
        UUID claimId = claim(publishedWish(autoOrganization, "還沒到期"), DONOR);

        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueFound").value(0));

        assertThat(claimStatusOf(claimId)).isEqualTo("CLAIMED");
    }

    @Test
    @DisplayName("已寄出的認領不算逾期")
    void shippedClaimsAreNotConsideredOverdue() throws Exception {
        UUID claimId = claim(publishedWish(autoOrganization, "已經寄了"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());
        makeOverdue(claimId);

        // 東西已經在路上了，逾期的定義是「沒寄」而不是「沒送達」
        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueFound").value(0));

        assertThat(claimStatusOf(claimId)).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("一次掃描能處理多筆，且兩種政策各自正確")
    void sweepHandlesMultipleClaimsOfBothPolicies() throws Exception {
        // 這裡同時驗證了批次更新的正確性：若對每筆各呼叫一次會清空持久化脈絡的
        // UPDATE，第二筆之後的變更就會遺失
        UUID autoWishA = publishedWish(autoOrganization, "自動 A");
        UUID autoWishB = publishedWish(autoOrganization, "自動 B");
        UUID manualWish = publishedWish(manualOrganization, "手動 C");

        makeOverdue(claim(autoWishA, DONOR));
        makeOverdue(claim(autoWishB, DONOR));
        makeOverdue(claim(manualWish, OTHER_DONOR));

        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueFound").value(3))
                .andExpect(jsonPath("$.autoReleased").value(2))
                .andExpect(jsonPath("$.wishesReturnedToWall").value(2))
                .andExpect(jsonPath("$.flaggedForOrganization").value(1));

        assertThat(wishStatusOf(autoWishA)).isEqualTo("AVAILABLE");
        assertThat(wishStatusOf(autoWishB)).isEqualTo("AVAILABLE");
        assertThat(wishStatusOf(manualWish)).isEqualTo("CLAIMED");
    }

    @Test
    @DisplayName("重複掃描不會重複釋回")
    void sweepingTwiceIsIdempotent() throws Exception {
        makeOverdue(claim(publishedWish(autoOrganization, "掃兩次"), DONOR));

        sweep();
        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueFound").value(0));
    }

    @Test
    @DisplayName("釋回會留下系統產生的稽核事件")
    void automaticReleaseIsRecordedInTheTimeline() throws Exception {
        UUID claimId = claim(publishedWish(autoOrganization, "稽核軌跡"), DONOR);
        makeOverdue(claimId);
        sweep();

        mvc.perform(as(get("/api/claims/{id}/timeline", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].eventType").value("RELEASED_AUTO"));

        Integer actorless = jdbc.queryForObject(
                "SELECT count(*) FROM claim_events "
                        + "WHERE event_type = 'RELEASED_AUTO' AND actor_user_id IS NULL",
                Integer.class);
        assertThat(actorless).as("排程觸發的事件沒有操作者，應為系統動作").isOne();
    }

    @Test
    @DisplayName("機構事後改政策不影響既有認領的快照")
    void changingThePolicyDoesNotAffectExistingClaims() throws Exception {
        UUID wishId = publishedWish(manualOrganization, "政策快照");
        UUID claimId = claim(wishId, DONOR);
        makeOverdue(claimId);

        // 機構在認領之後把政策改成自動
        manualOrganization.updateReleasePolicy(ReleasePolicy.AUTO, 3);
        organizations.save(manualOrganization);

        // 既有認領仍依當時的 MANUAL 快照處理，不會被無預警收回
        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoReleased").value(0))
                .andExpect(jsonPath("$.flaggedForOrganization").value(1));

        assertThat(claimStatusOf(claimId)).isEqualTo("CLAIMED");
    }

    // ------------------------------------------------------------ 存取控制

    @Test
    @DisplayName("排程端點需要 Google OIDC token，Firebase token 無效")
    void internalEndpointRejectsRegularUserTokens() throws Exception {
        // 內部端點走的是完全不同的驗證鏈，一般使用者的 token 在那裡沒有意義
        mvc.perform(as(post("/internal/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/jobs/release-expired-claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("手動觸發限管理員")
    void manualTriggerIsAdminOnly() throws Exception {
        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), DONOR))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), MANUAL_ORG))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/jobs/release-expired-claims"))
                .andExpect(status().isUnauthorized());
    }
}
