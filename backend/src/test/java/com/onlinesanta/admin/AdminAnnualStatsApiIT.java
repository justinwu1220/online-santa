package com.onlinesanta.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.claim.dto.ShipRequest;
import com.onlinesanta.common.TaiwanYear;
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

@DisplayName("監控中心的年度營運")
class AdminAnnualStatsApiIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_A = "org-a@example.org";
    private static final String ORG_B = "org-b@example.org";
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

    private Organization organizationA;
    private Organization organizationB;

    @BeforeEach
    void setUp() {
        organizationA = approvedOrganization("甲機構", ORG_A);
        organizationB = approvedOrganization("乙機構", ORG_B);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private Organization approvedOrganization(String name, String memberEmail) {
        Organization org = Organization.register(
                name, "王承辦", "contact@example.org", "02-1234-5678", "台北市中正區某某路 1 號", null);
        org.approve(null, "測試資料");
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

    private UUID claimAs(UUID wishId, String donorEmail) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donorEmail))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void backdateClaim(UUID claimId, Instant claimedAt) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET claimed_at = ? WHERE id = ?", Timestamp.from(claimedAt), claimId);
        entityManager.clear();
    }

    private void completeFully(UUID claimId, String orgMember) throws Exception {
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), orgMember))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/complete", claimId), orgMember))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 基本彙總

    @Test
    @DisplayName("年度營運總覽涵蓋新捐贈者、新機構、認領與完成率")
    void annualStatsAggregateAcrossThePlatform() throws Exception {
        int thisYear = TaiwanYear.currentYear();

        UUID claim1 = claimAs(publishedWish(organizationA, "甲的願望"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claim1),
                        new ShipRequest("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claim1, ORG_A);

        UUID claim2 = claimAs(publishedWish(organizationB, "乙的願望"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claim2),
                        new ReleaseRequest("臨時有事")), DONOR)).andExpect(status().isOk());

        mvc.perform(as(get("/api/admin/stats/annual")
                        .param("year", String.valueOf(thisYear)), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(thisYear))
                // setUp 建立了 2 個機構帳號 + 1 個捐贈者；機構帳號在存檔前就
                // joinOrganization() 把角色變成 ORG_MEMBER，只有捐贈者仍是 DONOR
                .andExpect(jsonPath("$.newDonors").value(1))
                .andExpect(jsonPath("$.newOrganizations").value(2))
                .andExpect(jsonPath("$.activeDonors").value(1))
                .andExpect(jsonPath("$.publishedWishes").value(2))
                .andExpect(jsonPath("$.claimed").value(2))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.completionRate").value(0.5))
                .andExpect(jsonPath("$.claimOutcomes.COMPLETED").value(1))
                .andExpect(jsonPath("$.claimOutcomes.CANCELLED").value(1))
                .andExpect(jsonPath("$.claimOutcomes.RELEASED").value(0));
    }

    @Test
    @DisplayName("認領結果分布沒有資料時補零，而不是消失")
    void claimOutcomesAreZeroFilledWhenAbsent() throws Exception {
        mvc.perform(as(get("/api/admin/stats/annual"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimOutcomes.COMPLETED").value(0))
                .andExpect(jsonPath("$.claimOutcomes.RELEASED").value(0))
                .andExpect(jsonPath("$.claimOutcomes.CANCELLED").value(0))
                .andExpect(jsonPath("$.monthlyClaims.length()").value(12));
    }

    // ------------------------------------------------------------ 機構完成排行

    @Test
    @DisplayName("機構完成排行只列前五名且依完成數排序，機構間互不可見於彼此的統計")
    void topOrganizationsRankingOrdersByCompletedCount() throws Exception {
        // 甲機構完成 2 筆、乙機構完成 1 筆
        for (String title : new String[]{"甲一", "甲二"}) {
            UUID claimId = claimAs(publishedWish(organizationA, title), DONOR);
            mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                            new ShipRequest("郵局", "R")), DONOR)).andExpect(status().isOk());
            completeFully(claimId, ORG_A);
        }
        UUID claimB = claimAs(publishedWish(organizationB, "乙一"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimB),
                        new ShipRequest("郵局", "R")), DONOR)).andExpect(status().isOk());
        completeFully(claimB, ORG_B);

        mvc.perform(as(get("/api/admin/stats/annual"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topOrganizations[0].organizationName").value("甲機構"))
                .andExpect(jsonPath("$.topOrganizations[0].completedCount").value(2))
                .andExpect(jsonPath("$.topOrganizations[1].organizationName").value("乙機構"))
                .andExpect(jsonPath("$.topOrganizations[1].completedCount").value(1));
    }

    // ------------------------------------------------------------ cohort 與年度邊界

    @Test
    @DisplayName("活躍捐贈者以認領年度為準，同一人多筆只算一次")
    void activeDonorsCountDistinctDonorsWithinTheYear() throws Exception {
        users.save(User.newDonor(TestJwtSupport.uidFor(OTHER_DONOR), OTHER_DONOR, "另一位民眾"));

        claimAs(publishedWish(organizationA, "願望一"), DONOR);
        claimAs(publishedWish(organizationA, "願望二"), DONOR); // 同一人再認領一筆
        claimAs(publishedWish(organizationB, "願望三"), OTHER_DONOR);

        int thisYear = TaiwanYear.currentYear();
        mvc.perform(as(get("/api/admin/stats/annual")
                        .param("year", String.valueOf(thisYear)), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeDonors").value(2))
                .andExpect(jsonPath("$.claimed").value(3));
    }

    @Test
    @DisplayName("cohort 制：完成數以認領年度計，不看實際完成的年份")
    void completedCountFollowsClaimYearAcrossThePlatform() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "跨年才完成"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        new ShipRequest("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claimId, ORG_A);

        backdateClaim(claimId, TaiwanYear.startOf(2025).plusSeconds(3600));

        mvc.perform(as(get("/api/admin/stats/annual").param("year", "2025"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(1))
                .andExpect(jsonPath("$.completed").value(1));
    }

    // ------------------------------------------------------------ 權限

    @Test
    @DisplayName("非管理員無法查詢年度營運統計")
    void nonAdminsCannotAccessAnnualStats() throws Exception {
        mvc.perform(as(get("/api/admin/stats/annual"), DONOR)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/stats/annual"), ORG_A)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats/annual")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ 每月分布下鑽

    @Test
    @DisplayName("月份邊界：台北 1/31 23:30 的認領歸屬 1 月，即使 UTC 已經是 2 月")
    void dailyClaimsRespectsTaipeiMonthBoundary() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "月底的認領"), DONOR);

        // 台北時間 2026-01-31 23:30，UTC 已經是 2026-02-01 15:30
        Instant taipeiMonthEnd = ZonedDateTime.of(2026, 1, 31, 23, 30, 0, 0, TaiwanYear.ZONE).toInstant();
        backdateClaim(claimId, taipeiMonthEnd);

        mvc.perform(as(get("/api/admin/stats/monthly")
                        .param("year", "2026").param("month", "1"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyClaims[30].day").value(31))
                .andExpect(jsonPath("$.dailyClaims[30].count").value(1));

        mvc.perform(as(get("/api/admin/stats/monthly")
                        .param("year", "2026").param("month", "2"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyClaims[0].count").value(0));
    }

    @Test
    @DisplayName("補零：二月沒資料的日子是 0，且天數依實際月長（2026 是平年，28 天），跨機構彙總")
    void dailyClaimsZeroFillAcrossOrganizations() throws Exception {
        UUID claimA = claimAs(publishedWish(organizationA, "甲機構二月的認領"), DONOR);
        UUID claimB = claimAs(publishedWish(organizationB, "乙機構二月的認領"), DONOR);
        Instant february15 = ZonedDateTime.of(2026, 2, 15, 10, 0, 0, 0, TaiwanYear.ZONE).toInstant();
        backdateClaim(claimA, february15);
        backdateClaim(claimB, february15);

        mvc.perform(as(get("/api/admin/stats/monthly")
                        .param("year", "2026").param("month", "2"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyClaims.length()").value(28))
                // 平台端彙總所有機構，兩筆都落在 2/15
                .andExpect(jsonPath("$.dailyClaims[14].day").value(15))
                .andExpect(jsonPath("$.dailyClaims[14].count").value(2))
                .andExpect(jsonPath("$.dailyClaims[0].count").value(0));
    }

    @Test
    @DisplayName("月份超出 1–12 範圍回 400")
    void monthOutOfRangeReturns400() throws Exception {
        mvc.perform(as(get("/api/admin/stats/monthly")
                        .param("year", "2026").param("month", "13"), ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MONTH"));
    }

    @Test
    @DisplayName("非管理員無法查詢單月每日分布，未登入回 401")
    void nonAdminsCannotAccessMonthlyStats() throws Exception {
        mvc.perform(as(get("/api/admin/stats/monthly")
                        .param("year", "2026").param("month", "1"), DONOR))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats/monthly").param("year", "2026").param("month", "1"))
                .andExpect(status().isUnauthorized());
    }
}
