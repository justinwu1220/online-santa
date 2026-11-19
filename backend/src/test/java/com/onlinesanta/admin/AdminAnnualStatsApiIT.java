package com.onlinesanta.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
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

@DisplayName("監控中心的檔期營運")
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

    private void backdateClaim(UUID claimId, Instant claimedAt) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET claimed_at = ? WHERE id = ?", Timestamp.from(claimedAt), claimId);
        entityManager.clear();
    }

    private Campaign campaign(String name, LocalDate start, LocalDate end, String prefix) {
        return campaignService.create(new CampaignRequest(name, null, null, null, start, end, start, end,
                start, end.minusDays(1), prefix, null, null, null));
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
    @DisplayName("營運總覽涵蓋新捐贈者、新機構、認領與完成率（沒有檔期時是「尚未建立檔期」）")
    void periodStatsAggregateAcrossThePlatform() throws Exception {

        UUID claim1 = claimAs(publishedWish(organizationA, "甲的願望"), DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claim1, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claim1),
                        ShipRequests.of("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claim1, ORG_A);

        UUID claim2 = claimAs(publishedWish(organizationB, "乙的願望"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claim2),
                        new ReleaseRequest("臨時有事")), DONOR)).andExpect(status().isOk());

        mvc.perform(as(get("/api/admin/stats/period"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.key").value("unassigned"))
                .andExpect(jsonPath("$.period.unassigned").value(true))
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
        mvc.perform(as(get("/api/admin/stats/period"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimOutcomes.COMPLETED").value(0))
                .andExpect(jsonPath("$.claimOutcomes.RELEASED").value(0))
                .andExpect(jsonPath("$.claimOutcomes.CANCELLED").value(0))
                .andExpect(jsonPath("$.monthlyClaims.length()").value(1))
                .andExpect(jsonPath("$.monthlyClaims[0].count").value(0));
    }

    // ------------------------------------------------------------ 機構完成排行

    @Test
    @DisplayName("機構完成排行只列前五名且依完成數排序，機構間互不可見於彼此的統計")
    void topOrganizationsRankingOrdersByCompletedCount() throws Exception {
        // 甲機構完成 2 筆、乙機構完成 1 筆
        for (String title : new String[]{"甲一", "甲二"}) {
            UUID claimId = claimAs(publishedWish(organizationA, title), DONOR);
            uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
            mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                            ShipRequests.of("郵局", "R")), DONOR)).andExpect(status().isOk());
            completeFully(claimId, ORG_A);
        }
        UUID claimB = claimAs(publishedWish(organizationB, "乙一"), DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimB, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimB),
                        ShipRequests.of("郵局", "R")), DONOR)).andExpect(status().isOk());
        completeFully(claimB, ORG_B);

        mvc.perform(as(get("/api/admin/stats/period"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topOrganizations[0].organizationName").value("甲機構"))
                .andExpect(jsonPath("$.topOrganizations[0].completedCount").value(2))
                .andExpect(jsonPath("$.topOrganizations[1].organizationName").value("乙機構"))
                .andExpect(jsonPath("$.topOrganizations[1].completedCount").value(1));
    }

    // ------------------------------------------------------------ cohort 與期間邊界

    @Test
    @DisplayName("活躍捐贈者以期間內的認領為準，同一人多筆只算一次")
    void activeDonorsCountDistinctDonorsWithinThePeriod() throws Exception {
        users.save(User.newDonor(OTHER_DONOR, "另一位民眾"));

        claimAs(publishedWish(organizationA, "願望一"), DONOR);
        claimAs(publishedWish(organizationA, "願望二"), DONOR); // 同一人再認領一筆
        claimAs(publishedWish(organizationB, "願望三"), OTHER_DONOR);

        mvc.perform(as(get("/api/admin/stats/period"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeDonors").value(2))
                .andExpect(jsonPath("$.claimed").value(3));
    }

    @Test
    @DisplayName("cohort 制：完成數以認領時所在的期間計，不看實際完成的時間")
    void completedCountFollowsClaimPeriod() throws Exception {
        Campaign earlier = campaign("2025 檔期", LocalDate.of(2025, 11, 25), LocalDate.of(2026, 1, 25), "T25");
        UUID claimId = claimAs(publishedWish(organizationA, "跨檔期才完成"), DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.of("郵局", "R1")), DONOR)).andExpect(status().isOk());
        completeFully(claimId, ORG_A);

        backdateClaim(claimId, taipei(2025, 12, 20, 10, 0));

        mvc.perform(as(get("/api/admin/stats/period").param("period", earlier.getId().toString()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(1))
                .andExpect(jsonPath("$.completed").value(1));
    }

    @Test
    @DisplayName("統計期間：[本檔期勸募開始日, 下個檔期勸募開始日)，每筆資料剛好屬於一個期間，月份依時間排序且不疊加跨年")
    void everyDayBelongsToExactlyOnePeriod() throws Exception {
        Campaign first = campaign("2025 檔期", LocalDate.of(2025, 11, 25), LocalDate.of(2026, 1, 25), "T25");
        Campaign second = campaign("2026 檔期", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), "T26");
        // 11/24 23:30 屬於第一個檔期，11/25 00:00 起是第二個檔期
        backdateClaim(claimAs(publishedWish(organizationA, "十二月"), DONOR), taipei(2025, 12, 10, 12, 0));
        backdateClaim(claimAs(publishedWish(organizationA, "開始前一刻"), DONOR), taipei(2026, 11, 24, 23, 30));
        backdateClaim(claimAs(publishedWish(organizationA, "開始當刻"), DONOR), taipei(2026, 11, 25, 0, 0));
        // 同一位捐贈者同時進行中的認領有上限，第四筆換另一位
        users.save(User.newDonor(OTHER_DONOR, "另一位民眾"));
        backdateClaim(claimAs(publishedWish(organizationB, "隔年一月"), OTHER_DONOR), taipei(2027, 1, 10, 12, 0));

        mvc.perform(as(get("/api/admin/stats/period").param("period", first.getId().toString()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(2))
                .andExpect(jsonPath("$.period.name").value("2025 檔期"))
                .andExpect(jsonPath("$.period.start").value("2025-11-25"))
                .andExpect(jsonPath("$.period.end").value("2026-11-24"))
                .andExpect(jsonPath("$.monthlyClaims.length()").value(13))
                .andExpect(jsonPath("$.monthlyClaims[0].year").value(2025))
                .andExpect(jsonPath("$.monthlyClaims[0].month").value(11))
                .andExpect(jsonPath("$.monthlyClaims[1].month").value(12))
                .andExpect(jsonPath("$.monthlyClaims[1].count").value(1))
                .andExpect(jsonPath("$.monthlyClaims[12].year").value(2026))
                .andExpect(jsonPath("$.monthlyClaims[12].month").value(11))
                .andExpect(jsonPath("$.monthlyClaims[12].count").value(1));

        mvc.perform(as(get("/api/admin/stats/period").param("period", second.getId().toString()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(2))
                .andExpect(jsonPath("$.period.start").value("2026-11-25"))
                .andExpect(jsonPath("$.period.end").value("2027-01-25"))
                .andExpect(jsonPath("$.monthlyClaims.length()").value(3))
                .andExpect(jsonPath("$.monthlyClaims[0].year").value(2026))
                .andExpect(jsonPath("$.monthlyClaims[0].month").value(11))
                .andExpect(jsonPath("$.monthlyClaims[0].count").value(1))
                .andExpect(jsonPath("$.monthlyClaims[2].year").value(2027))
                .andExpect(jsonPath("$.monthlyClaims[2].month").value(1))
                .andExpect(jsonPath("$.monthlyClaims[2].count").value(1));
    }

    @Test
    @DisplayName("兩個檔期之間的空檔歸前一個檔期；第一個檔期之前的資料也算進第一個檔期，不會漏掉")
    void gapsBelongToTheEarlierCampaignAndEarlierDataToTheFirst() throws Exception {
        Campaign only = campaign("2025 檔期", LocalDate.of(2025, 11, 25), LocalDate.of(2026, 1, 25), "T25");
        Campaign next = campaign("2026 檔期", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), "T26");
        backdateClaim(claimAs(publishedWish(organizationA, "更早的認領"), DONOR), taipei(2024, 6, 1, 9, 0));
        backdateClaim(claimAs(publishedWish(organizationA, "空檔的認領"), DONOR), taipei(2026, 6, 1, 9, 0));

        mvc.perform(as(get("/api/admin/stats/period").param("period", only.getId().toString()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(2))
                .andExpect(jsonPath("$.period.start").value("2024-06-01"))
                .andExpect(jsonPath("$.monthlyClaims[0].year").value(2024))
                .andExpect(jsonPath("$.monthlyClaims[0].month").value(6))
                .andExpect(jsonPath("$.monthlyClaims[0].count").value(1));
        mvc.perform(as(get("/api/admin/stats/period").param("period", next.getId().toString()), ADMIN))
                .andExpect(jsonPath("$.claimed").value(0));
    }

    @Test
    @DisplayName("最後一個檔期往後一路到今天，沒有另外的「尚未建立檔期」；預設選項是它")
    void theLastCampaignExtendsToToday() throws Exception {
        LocalDate today = LocalDate.now(TaiwanYear.ZONE);
        Campaign ended = campaign("已結束檔期", today.minusDays(100), today.minusDays(40), "TEND");
        claimAs(publishedWish(organizationA, "今天的認領"), DONOR);

        mvc.perform(as(get("/api/admin/stats/periods"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultKey").value(ended.getId().toString()))
                .andExpect(jsonPath("$.periods.length()").value(1))
                .andExpect(jsonPath("$.periods[0].unassigned").value(false))
                .andExpect(jsonPath("$.periods[0].end").value(today.toString()));

        mvc.perform(as(get("/api/admin/stats/period").param("period", ended.getId().toString()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(1));
    }

    @Test
    @DisplayName("進行中的檔期涵蓋今天時是預設選項，且沒有「尚未建立檔期」")
    void theCurrentCampaignIsTheDefault() throws Exception {
        LocalDate today = LocalDate.now(TaiwanYear.ZONE);
        Campaign current = campaign("進行中", today.minusDays(5), today.plusDays(30), "TNOW");

        mvc.perform(as(get("/api/admin/stats/periods"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultKey").value(current.getId().toString()))
                .andExpect(jsonPath("$.periods.length()").value(1));
    }

    @Test
    @DisplayName("找不到的期間回 404")
    void unknownPeriodReturns404() throws Exception {
        mvc.perform(as(get("/api/admin/stats/period").param("period", UUID.randomUUID().toString()), ADMIN))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/admin/stats/period").param("period", "abc"), ADMIN))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ 權限

    @Test
    @DisplayName("非管理員無法查詢營運統計")
    void nonAdminsCannotAccessPeriodStats() throws Exception {
        for (String path : new String[]{"/api/admin/stats/period", "/api/admin/stats/periods"}) {
            mvc.perform(as(get(path), DONOR)).andExpect(status().isForbidden());
            mvc.perform(as(get(path), ORG_A)).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
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
