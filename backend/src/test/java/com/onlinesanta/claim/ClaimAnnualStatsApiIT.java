package com.onlinesanta.claim;

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

@DisplayName("捐贈者的年度回顧")
class ClaimAnnualStatsApiIT extends ApiIntegrationTest {

    private static final String ORG_USER = "org@example.org";
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

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = approvedOrganization("送禮之家", ORG_USER);
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

    private UUID publishedWish(String title) {
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

    /**
     * 把認領與完成時間改到指定的 Instant，模擬跨年的檔期作業。
     *
     * <p>原生 SQL 繞過了 Hibernate，前後必須 flush 與 clear，比照 {@code ClaimReleaseIT}
     * 的做法。
     */
    private void backdate(UUID claimId, Instant claimedAt, Instant completedAt) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET claimed_at = ?, completed_at = ? WHERE id = ?",
                Timestamp.from(claimedAt),
                completedAt == null ? null : Timestamp.from(completedAt),
                claimId);
        entityManager.clear();
    }

    private void completeFully(UUID claimId, String donorEmail) throws Exception {
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        new ShipRequest("郵局", "R123")), donorEmail))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/complete", claimId), ORG_USER))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 年度篩選清單

    @Test
    @DisplayName("year 參數篩選我的認領清單")
    void listMineFiltersByYear() throws Exception {
        UUID claim2025 = claimAs(publishedWish("2025 年的認領"), DONOR);
        backdate(claim2025, TaiwanYear.startOf(2025).plusSeconds(3600), null);

        UUID claim2026 = claimAs(publishedWish("2026 年的認領"), DONOR);
        backdate(claim2026, TaiwanYear.startOf(2026).plusSeconds(3600), null);

        mvc.perform(as(get("/api/claims/me").param("year", "2025"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("2025 年的認領"));

        mvc.perform(as(get("/api/claims/me").param("year", "2026"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("2026 年的認領"));

        // 不帶 year 就是全部
        mvc.perform(as(get("/api/claims/me"), DONOR))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ------------------------------------------------------------ 年度邊界

    @Test
    @DisplayName("台北跨年夜的認領歸屬正確的年度，即使 UTC 已經是隔年")
    void claimedAtRespectsTaipeiYearBoundary() throws Exception {
        UUID claimId = claimAs(publishedWish("跨年夜的認領"), DONOR);

        // 台北時間 2025-12-31 23:30，UTC 已經是 2026-01-01 15:30
        Instant taipeiNewYearsEve = ZonedDateTime
                .of(2025, 12, 31, 23, 30, 0, 0, TaiwanYear.ZONE)
                .toInstant();
        backdate(claimId, taipeiNewYearsEve, null);

        mvc.perform(as(get("/api/claims/me/annual-summary").param("year", "2025"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedCount").value(1));

        mvc.perform(as(get("/api/claims/me/annual-summary").param("year", "2026"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedCount").value(0));

        // 反向邊界：台北 2026-01-01 00:00:00 已經算 2026 年，即使非常接近午夜
        Instant taipeiNewYearsMidnight = ZonedDateTime
                .of(2026, 1, 1, 0, 0, 0, 0, TaiwanYear.ZONE)
                .toInstant();
        backdate(claimId, taipeiNewYearsMidnight, null);

        mvc.perform(as(get("/api/claims/me/annual-summary").param("year", "2026"), DONOR))
                .andExpect(jsonPath("$.claimedCount").value(1));
        mvc.perform(as(get("/api/claims/me/annual-summary").param("year", "2025"), DONOR))
                .andExpect(jsonPath("$.claimedCount").value(0));
    }

    // ------------------------------------------------------------ cohort 歸屬

    @Test
    @DisplayName("cohort 制：Y 年認領、Y+1 年完成，完成數仍算在 Y 年")
    void completionCountFollowsTheClaimYearNotTheCompletionYear() throws Exception {
        UUID claimId = claimAs(publishedWish("跨年才完成"), DONOR);
        completeFully(claimId, DONOR);

        // 認領於 2025 年末，完成於 2026 年初
        Instant claimedAt = TaiwanYear.startOf(2025).plus(360, ChronoUnit.DAYS);
        Instant completedAt = TaiwanYear.startOf(2026).plusSeconds(3600 * 24 * 10L);
        backdate(claimId, claimedAt, completedAt);

        mvc.perform(as(get("/api/claims/me/annual-summary").param("year", "2025"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedCount").value(1))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.childrenHelped").value(1))
                .andExpect(jsonPath("$.organizationsSupported").value(1));

        // 2026 年沒有任何一筆是「該年認領」的，即使完成動作發生在這一年
        mvc.perform(as(get("/api/claims/me/annual-summary").param("year", "2026"), DONOR))
                .andExpect(jsonPath("$.claimedCount").value(0))
                .andExpect(jsonPath("$.completedCount").value(0));
    }

    @Test
    @DisplayName("年度小結：認領數、完成數、送禮孩子數與支持機構數")
    void annualSummaryAggregatesCorrectly() throws Exception {
        Organization anotherOrg = approvedOrganization("另一家機構", "another-org@example.org");

        UUID claim1 = claimAs(publishedWish("願望一"), DONOR);
        completeFully(claim1, DONOR);

        UUID wishId2 = publishedWish("願望二");
        UUID claim2 = claimAs(wishId2, DONOR);
        completeFully(claim2, DONOR);

        Wish wishAtOtherOrg = Wish.draft(anotherOrg, "小雨", AgeRange.AGE_4_6, "拼圖",
                "另一家的願望", "描述", WishCategory.TOY, PriceRange.UNDER_500);
        wishAtOtherOrg.publish();
        UUID wishId3 = wishes.save(wishAtOtherOrg).getId();
        claimAs(wishId3, DONOR); // 未完成

        int thisYear = TaiwanYear.currentYear();
        mvc.perform(as(get("/api/claims/me/annual-summary").param("year", String.valueOf(thisYear)),
                        DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(thisYear))
                .andExpect(jsonPath("$.claimedCount").value(3))
                .andExpect(jsonPath("$.completedCount").value(2))
                .andExpect(jsonPath("$.childrenHelped").value(3))
                .andExpect(jsonPath("$.organizationsSupported").value(2))
                .andExpect(jsonPath("$.availableYears[0]").value(thisYear));
    }

    @Test
    @DisplayName("沒有任何認領時，可選年份只回今年")
    void availableYearsDefaultsToCurrentYearWithNoData() throws Exception {
        mvc.perform(as(get("/api/claims/me/annual-summary"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableYears.length()").value(1))
                .andExpect(jsonPath("$.availableYears[0]").value(TaiwanYear.currentYear()))
                .andExpect(jsonPath("$.claimedCount").value(0));
    }

    // ------------------------------------------------------------ 權限

    @Test
    @DisplayName("未登入無法查詢年度小結")
    void anonymousCannotViewAnnualSummary() throws Exception {
        mvc.perform(get("/api/claims/me/annual-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("年度小結不含其他捐贈者的資料")
    void annualSummaryIsScopedToTheRequestingDonor() throws Exception {
        String otherDonor = "other-donor@example.com";
        users.save(User.newDonor(TestJwtSupport.uidFor(otherDonor), otherDonor, "另一位民眾"));
        claimAs(publishedWish("別人的認領"), otherDonor);

        int thisYear = TaiwanYear.currentYear();
        mvc.perform(as(get("/api/claims/me/annual-summary").param("year", String.valueOf(thisYear)),
                        DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedCount").value(0));
    }
}
