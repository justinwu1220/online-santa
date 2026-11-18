package com.onlinesanta.organization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignService;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.support.ShipRequests;
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

@DisplayName("機構後台的檔期回顧")
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

    @Autowired
    CampaignService campaignService;

    @PersistenceContext
    EntityManager entityManager;

    private Organization organizationA;
    private Organization organizationB;

    @BeforeEach
    void setUp() {
        organizationA = approvedOrganization("甲機構", ORG_A);
        organizationB = approvedOrganization("乙機構", ORG_B);
        users.save(User.newDonor(DONOR, "熱心民眾"));
    }

    private Organization approvedOrganization(String name, String memberEmail) {
        Organization org = Organization.register(
                name, "王承辦", "contact@example.org", "02-1234-5678", "台北市中正區某某路 1 號", null);
        org.approve(null, "測試資料");
        organizations.save(org);

        User member = User.newDonor(memberEmail, memberEmail);
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

    private Campaign campaign(String name, LocalDate start, LocalDate end, String prefix) {
        return campaignService.create(new CampaignRequest(name, null, null, null, start, end, start, end,
                null, prefix, null, null, null));
    }

    private static Instant taipei(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TaiwanYear.ZONE).toInstant();
    }

    private void completeFully(UUID claimId, String orgMember) throws Exception {
        completeAsOrganization(claimId, orgMember)
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 基本彙總

    @Test
    @DisplayName("統計涵蓋新增願望、認領、完成率（沒有檔期時是「尚未建立檔期」）")
    void periodStatsAggregateCorrectly() throws Exception {
        UUID wish1 = publishedWish(organizationA, "願望一");
        UUID claim1 = claimAs(wish1, DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claim1, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claim1),
                        ShipRequests.of("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claim1, ORG_A);

        UUID wish2 = publishedWish(organizationA, "願望二");
        UUID claim2 = claimAs(wish2, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claim2),
                        new ReleaseRequest("臨時有事")), DONOR)).andExpect(status().isOk());

        publishedWish(organizationA, "願望三（未認領）");

        mvc.perform(as(get("/api/organizations/me/stats/period"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.key").value("unassigned"))
                .andExpect(jsonPath("$.newWishes").value(3))
                .andExpect(jsonPath("$.claimed").value(2))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.completionRate").value(0.5))
                .andExpect(jsonPath("$.cancelled").value(1))
                .andExpect(jsonPath("$.released").value(0));
    }

    @Test
    @DisplayName("願望歸期用 createdAt，不是發布時間")
    void newWishesCountUsesCreatedAtNotPublishedAt() throws Exception {
        Campaign first = campaign("2025 檔期", LocalDate.of(2025, 11, 25), LocalDate.of(2026, 1, 25), "T25");
        Campaign second = campaign("2026 檔期", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), "T26");

        // 2025 檔期期間建立、今天才發布（published_at 是現在，created_at 在 2025）
        UUID wishId = publishedWish(organizationA, "早就建立、今天才上架");
        backdateWish(wishId, taipei(2025, 12, 1, 10, 0));

        mvc.perform(as(get("/api/organizations/me/stats/period").param("period", first.getId().toString()), ORG_A))
                .andExpect(jsonPath("$.newWishes").value(1));
        mvc.perform(as(get("/api/organizations/me/stats/period").param("period", second.getId().toString()), ORG_A))
                .andExpect(jsonPath("$.newWishes").value(0));
    }

    // ------------------------------------------------------------ cohort 與期間結束後完成

    @Test
    @DisplayName("期間結束後才完成：該檔期認領、之後才完成的筆數會被量化")
    void completionsAfterThePeriodAreCounted() throws Exception {
        Campaign first = campaign("2025 檔期", LocalDate.of(2025, 11, 25), LocalDate.of(2026, 1, 25), "T25");
        Campaign second = campaign("2026 檔期", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), "T26");
        UUID wishId = publishedWish(organizationA, "拖到檔期結束後完成");
        UUID claimId = claimAs(wishId, DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.of("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claimId, ORG_A);

        backdateClaim(claimId, taipei(2026, 1, 20, 10, 0), taipei(2026, 2, 5, 10, 0));

        mvc.perform(as(get("/api/organizations/me/stats/period").param("period", first.getId().toString()), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(1))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.completedAfterPeriod").value(1))
                .andExpect(jsonPath("$.averageCompletionDays").isNumber());

        mvc.perform(as(get("/api/organizations/me/stats/period").param("period", second.getId().toString()), ORG_A))
                .andExpect(jsonPath("$.claimed").value(0))
                .andExpect(jsonPath("$.completedAfterPeriod").value(0));
    }

    @Test
    @DisplayName("逾期自動釋回會計入 autoReleasedCount，並算進 released 總數")
    void autoReleasedCountsAreIncludedInReleasedTotal() throws Exception {
        organizationA.updateReleasePolicy(ReleasePolicy.AUTO, 7);
        organizations.save(organizationA);

        UUID claimId = claimAs(publishedWish(organizationA, "會被自動釋回"), DONOR);
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)), claimId);
        entityManager.clear();

        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), "platform-admin@example.com"))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/organizations/me/stats/period"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(1))
                .andExpect(jsonPath("$.autoReleasedCount").value(1));
    }

    // ------------------------------------------------------------ 每月分布與期間選單

    @Test
    @DisplayName("每月認領分布涵蓋期間的每個月份（含年份、依時間排序），沒有資料的月份補零")
    void monthlyClaimsAreZeroFilledForEveryMonthOfThePeriod() throws Exception {
        Campaign campaign = campaign("2025 檔期", LocalDate.of(2025, 11, 25), LocalDate.of(2026, 1, 25), "T25");
        UUID claimId = claimAs(publishedWish(organizationA, "十二月的認領"), DONOR);
        backdateClaim(claimId, taipei(2025, 12, 15, 10, 0), null);

        mvc.perform(as(get("/api/organizations/me/stats/period").param("period", campaign.getId().toString()), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyClaims.length()").value(3))
                .andExpect(jsonPath("$.monthlyClaims[0].year").value(2025))
                .andExpect(jsonPath("$.monthlyClaims[0].month").value(11))
                .andExpect(jsonPath("$.monthlyClaims[0].count").value(0))
                .andExpect(jsonPath("$.monthlyClaims[1].month").value(12))
                .andExpect(jsonPath("$.monthlyClaims[1].count").value(1))
                .andExpect(jsonPath("$.monthlyClaims[2].year").value(2026))
                .andExpect(jsonPath("$.monthlyClaims[2].month").value(1));
    }

    @Test
    @DisplayName("期間選單：沒有檔期時只有「尚未建立檔期」；有檔期後由新到舊，預設是涵蓋今天的檔期")
    void periodsListFollowsTheCampaigns() throws Exception {
        mvc.perform(as(get("/api/organizations/me/stats/periods"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultKey").value("unassigned"))
                .andExpect(jsonPath("$.periods.length()").value(1));

        LocalDate today = LocalDate.now(TaiwanYear.ZONE);
        Campaign past = campaign("去年檔期", today.minusDays(400), today.minusDays(340), "TOLD");
        Campaign current = campaign("今年檔期", today.minusDays(5), today.plusDays(30), "TNOW");

        mvc.perform(as(get("/api/organizations/me/stats/periods"), ORG_A))
                .andExpect(jsonPath("$.defaultKey").value(current.getId().toString()))
                // 兩個檔期之間的空檔算進後一個檔期的週期；進行中的檔期尚未結束，沒有「尚未建立檔期」
                .andExpect(jsonPath("$.periods.length()").value(2))
                .andExpect(jsonPath("$.periods[0].name").value("今年檔期"))
                .andExpect(jsonPath("$.periods[0].start").value(today.minusDays(339).toString()))
                .andExpect(jsonPath("$.periods[1].key").value(past.getId().toString()));
        // 機構看不到許可文號等檔期設定
        mvc.perform(as(get("/api/organizations/me/stats/periods"), ORG_A))
                .andExpect(jsonPath("$.periods[0].permitNo").doesNotExist());
    }

    // ------------------------------------------------------------ 機構隔離與權限

    @Test
    @DisplayName("機構彼此看不到對方的統計")
    void statsAreIsolatedPerOrganization() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "甲機構的認領"), DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.of("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claimId, ORG_A);

        mvc.perform(as(get("/api/organizations/me/stats/period"), ORG_A))
                .andExpect(jsonPath("$.claimed").value(1))
                .andExpect(jsonPath("$.completed").value(1));

        // 乙機構完全看不到甲機構的數字
        mvc.perform(as(get("/api/organizations/me/stats/period"), ORG_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(0))
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.newWishes").value(0));
    }

    @Test
    @DisplayName("非機構成員無法查詢機構的統計")
    void nonOrganizationMembersCannotAccessStats() throws Exception {
        for (String path : new String[]{"/api/organizations/me/stats/period", "/api/organizations/me/stats/periods"}) {
            mvc.perform(as(get(path), DONOR))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
        }
    }

    @Test
    @DisplayName("未登入無法查詢機構的統計")
    void anonymousCannotAccessStats() throws Exception {
        mvc.perform(get("/api/organizations/me/stats/period")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/organizations/me/stats/periods")).andExpect(status().isUnauthorized());
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
