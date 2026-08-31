package com.onlinesanta.organization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("機構後台的年度回顧")
class OrganizationAnnualStatsApiIT extends ApiIntegrationTest {

    private static final String ORG_A = "org-a@example.org";
    private static final String ORG_B = "org-b@example.org";
    private static final String DONOR = "donor@example.com";

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

    private void backdateClaim(UUID claimId, Instant claimedAt, Instant completedAt) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET claimed_at = ?, completed_at = ? WHERE id = ?",
                Timestamp.from(claimedAt),
                completedAt == null ? null : Timestamp.from(completedAt),
                claimId);
        entityManager.clear();
    }

    private void backdateWish(UUID wishId, Instant createdAt) {
        entityManager.flush();
        jdbc.update("UPDATE wishes SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), wishId);
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
    @DisplayName("年度統計涵蓋新增願望、認領、完成率")
    void annualStatsAggregateCorrectly() throws Exception {
        int thisYear = TaiwanYear.currentYear();

        UUID wish1 = publishedWish(organizationA, "願望一");
        UUID claim1 = claimAs(wish1, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claim1),
                        new ShipRequest("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claim1, ORG_A);

        UUID wish2 = publishedWish(organizationA, "願望二");
        UUID claim2 = claimAs(wish2, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claim2),
                        new ReleaseRequest("臨時有事")), DONOR)).andExpect(status().isOk());

        publishedWish(organizationA, "願望三（未認領）");

        mvc.perform(as(get("/api/organizations/me/stats/annual")
                        .param("year", String.valueOf(thisYear)), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(thisYear))
                .andExpect(jsonPath("$.newWishes").value(3))
                .andExpect(jsonPath("$.claimed").value(2))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.completionRate").value(0.5))
                .andExpect(jsonPath("$.cancelled").value(1))
                .andExpect(jsonPath("$.released").value(0));
    }

    @Test
    @DisplayName("願望歸年用 createdAt，即使重新上架也不受影響")
    void newWishesCountUsesCreatedAtNotPublishedAt() throws Exception {
        int thisYear = TaiwanYear.currentYear();
        int lastYear = thisYear - 1;

        UUID wishId = publishedWish(organizationA, "去年建立、今年重新上架");
        backdateWish(wishId, TaiwanYear.startOf(lastYear).plusSeconds(3600));

        // 下架再重新上架：published_at 只在第一次寫入，createdAt 不會變
        mvc.perform(as(post("/api/wishes/{id}/unpublish", wishId), ORG_A))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/wishes/{id}/publish", wishId), ORG_A))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/organizations/me/stats/annual")
                        .param("year", String.valueOf(lastYear)), ORG_A))
                .andExpect(jsonPath("$.newWishes").value(1));
        mvc.perform(as(get("/api/organizations/me/stats/annual")
                        .param("year", String.valueOf(thisYear)), ORG_A))
                .andExpect(jsonPath("$.newWishes").value(0));
    }

    // ------------------------------------------------------------ cohort 與跨年完成

    @Test
    @DisplayName("跨年完成：該年度認領、隔年完成的筆數會被量化")
    void crossYearCompletionsAreCounted() throws Exception {
        UUID wishId = publishedWish(organizationA, "跨年完成");
        UUID claimId = claimAs(wishId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        new ShipRequest("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claimId, ORG_A);

        Instant claimedAt = TaiwanYear.startOf(2025).plus(360, ChronoUnit.DAYS);
        Instant completedAt = TaiwanYear.startOf(2026).plusSeconds(3600 * 24 * 5L);
        backdateClaim(claimId, claimedAt, completedAt);

        mvc.perform(as(get("/api/organizations/me/stats/annual").param("year", "2025"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(1))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.crossYearCompletions").value(1))
                .andExpect(jsonPath("$.averageCompletionDays").isNumber());

        mvc.perform(as(get("/api/organizations/me/stats/annual").param("year", "2026"), ORG_A))
                .andExpect(jsonPath("$.claimed").value(0))
                .andExpect(jsonPath("$.crossYearCompletions").value(0));
    }

    @Test
    @DisplayName("逾期自動釋回會計入 autoReleasedCount，並算進 released 總數")
    void autoReleasedCountsAreIncludedInReleasedTotal() throws Exception {
        organizationA.updateReleasePolicy(ReleasePolicy.AUTO, 7);
        organizations.save(organizationA);

        UUID claimId = claimAs(publishedWish(organizationA, "會被自動釋回"), DONOR);
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), claimId);
        entityManager.clear();

        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), "platform-admin@example.com"))
                .andExpect(status().isOk());

        int thisYear = TaiwanYear.currentYear();
        mvc.perform(as(get("/api/organizations/me/stats/annual")
                        .param("year", String.valueOf(thisYear)), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(1))
                .andExpect(jsonPath("$.autoReleasedCount").value(1));
    }

    // ------------------------------------------------------------ 每月分布與可選年份

    @Test
    @DisplayName("每月認領分布固定回傳 12 個月，沒有資料的月份補零")
    void monthlyClaimsAreZeroFilledForAllTwelveMonths() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "三月的認領"), DONOR);
        Instant march = TaiwanYear.startOf(2025).plus(65, ChronoUnit.DAYS); // 落在三月
        backdateClaim(claimId, march, null);

        mvc.perform(as(get("/api/organizations/me/stats/annual").param("year", "2025"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyClaims.length()").value(12))
                .andExpect(jsonPath("$.monthlyClaims[2].month").value(3))
                .andExpect(jsonPath("$.monthlyClaims[2].count").value(1))
                .andExpect(jsonPath("$.monthlyClaims[0].count").value(0));
    }

    @Test
    @DisplayName("可選年份由最早一筆認領推導，沒有認領時只回今年")
    void availableYearsAreDerivedFromEarliestClaim() throws Exception {
        mvc.perform(as(get("/api/organizations/me/stats/annual"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableYears.length()").value(1))
                .andExpect(jsonPath("$.availableYears[0]").value(TaiwanYear.currentYear()));

        UUID claimId = claimAs(publishedWish(organizationA, "兩年前的認領"), DONOR);
        int twoYearsAgo = TaiwanYear.currentYear() - 2;
        backdateClaim(claimId, TaiwanYear.startOf(twoYearsAgo).plusSeconds(3600), null);

        mvc.perform(as(get("/api/organizations/me/stats/annual"), ORG_A))
                .andExpect(jsonPath("$.availableYears.length()").value(3))
                .andExpect(jsonPath("$.availableYears[0]").value(TaiwanYear.currentYear()))
                .andExpect(jsonPath("$.availableYears[2]").value(twoYearsAgo));
    }

    // ------------------------------------------------------------ 機構隔離與權限

    @Test
    @DisplayName("機構彼此看不到對方的年度統計")
    void statsAreIsolatedPerOrganization() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "甲機構的認領"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        new ShipRequest("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claimId, ORG_A);

        int thisYear = TaiwanYear.currentYear();
        mvc.perform(as(get("/api/organizations/me/stats/annual")
                        .param("year", String.valueOf(thisYear)), ORG_A))
                .andExpect(jsonPath("$.claimed").value(1))
                .andExpect(jsonPath("$.completed").value(1));

        // 乙機構完全看不到甲機構的數字
        mvc.perform(as(get("/api/organizations/me/stats/annual")
                        .param("year", String.valueOf(thisYear)), ORG_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(0))
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.newWishes").value(0));
    }

    @Test
    @DisplayName("非機構成員無法查詢機構的年度統計")
    void nonOrganizationMembersCannotAccessStats() throws Exception {
        mvc.perform(as(get("/api/organizations/me/stats/annual"), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    @Test
    @DisplayName("未登入無法查詢機構的年度統計")
    void anonymousCannotAccessStats() throws Exception {
        mvc.perform(get("/api/organizations/me/stats/annual"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ 每月分布下鑽

    @Test
    @DisplayName("月份邊界：台北 1/31 23:30 的認領歸屬 1 月，即使 UTC 已經是 2 月")
    void dailyClaimsRespectsTaipeiMonthBoundary() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "月底的認領"), DONOR);

        // 台北時間 2026-01-31 23:30，UTC 已經是 2026-02-01 15:30
        Instant taipeiMonthEnd = ZonedDateTime.of(2026, 1, 31, 23, 30, 0, 0, TaiwanYear.ZONE).toInstant();
        backdateClaim(claimId, taipeiMonthEnd, null);

        mvc.perform(as(get("/api/organizations/me/stats/monthly")
                        .param("year", "2026").param("month", "1"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyClaims[30].day").value(31))
                .andExpect(jsonPath("$.dailyClaims[30].count").value(1));

        mvc.perform(as(get("/api/organizations/me/stats/monthly")
                        .param("year", "2026").param("month", "2"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyClaims[0].day").value(1))
                .andExpect(jsonPath("$.dailyClaims[0].count").value(0));
    }

    @Test
    @DisplayName("補零：二月沒資料的日子是 0，且天數依實際月長（2026 是平年，28 天）")
    void dailyClaimsZeroFillAccordingToActualMonthLength() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "二月的認領"), DONOR);
        Instant february15 = ZonedDateTime.of(2026, 2, 15, 10, 0, 0, 0, TaiwanYear.ZONE).toInstant();
        backdateClaim(claimId, february15, null);

        mvc.perform(as(get("/api/organizations/me/stats/monthly")
                        .param("year", "2026").param("month", "2"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyClaims.length()").value(28))
                .andExpect(jsonPath("$.dailyClaims[14].day").value(15))
                .andExpect(jsonPath("$.dailyClaims[14].count").value(1))
                .andExpect(jsonPath("$.dailyClaims[0].count").value(0));
    }

    @Test
    @DisplayName("機構彼此看不到對方的單月每日分布")
    void dailyClaimsAreIsolatedPerOrganization() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "甲機構九月的認領"), DONOR);
        Instant september = ZonedDateTime.of(2026, 9, 5, 10, 0, 0, 0, TaiwanYear.ZONE).toInstant();
        backdateClaim(claimId, september, null);

        mvc.perform(as(get("/api/organizations/me/stats/monthly")
                        .param("year", "2026").param("month", "9"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyClaims[4].count").value(1));

        mvc.perform(as(get("/api/organizations/me/stats/monthly")
                        .param("year", "2026").param("month", "9"), ORG_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyClaims[4].count").value(0));
    }

    @Test
    @DisplayName("月份超出 1–12 範圍回 400")
    void monthOutOfRangeReturns400() throws Exception {
        mvc.perform(as(get("/api/organizations/me/stats/monthly")
                        .param("year", "2026").param("month", "13"), ORG_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MONTH"));

        mvc.perform(as(get("/api/organizations/me/stats/monthly")
                        .param("year", "2026").param("month", "0"), ORG_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MONTH"));
    }

    @Test
    @DisplayName("未登入無法查詢單月每日分布")
    void anonymousCannotAccessMonthlyStats() throws Exception {
        mvc.perform(get("/api/organizations/me/stats/monthly")
                        .param("year", "2026").param("month", "1"))
                .andExpect(status().isUnauthorized());
    }
}
