package com.onlinesanta.organization;

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

/**
 * 機構後台「願望管理」與「認領管理」的年度篩選。
 *
 * <p>做法比照監控中心的年度篩選（{@code AdminMonitoringIT}），差異只在多加了
 * 機構隔離的驗證：願望以 createdAt 歸年，認領以 claimedAt 歸年（cohort 制，
 * 與年度回顧口徑一致）。逾期提醒刻意不支援年度篩選，見
 * {@link com.onlinesanta.claim.ClaimService#listOverdueForMyOrganization} 的說明。
 */
@DisplayName("機構後台的年度篩選")
class OrganizationCatalogYearFilterIT extends ApiIntegrationTest {

    private static final String ORG_A = "catalog-year-a@example.org";
    private static final String ORG_B = "catalog-year-b@example.org";
    private static final String DONOR = "catalog-year-donor@example.com";

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

    private void backdateWish(UUID wishId, Instant createdAt) {
        entityManager.flush();
        jdbc.update("UPDATE wishes SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), wishId);
        entityManager.clear();
    }

    private void backdateClaim(UUID claimId, Instant claimedAt) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET claimed_at = ? WHERE id = ?", Timestamp.from(claimedAt), claimId);
        entityManager.clear();
    }

    // ================================================================ 願望管理：createdAt

    @Test
    @DisplayName("願望清單以 createdAt 的年度篩選")
    void wishListFiltersByCreatedAtYear() throws Exception {
        UUID oldWish = publishedWish(organizationA, "去年的願望");
        backdateWish(oldWish, TaiwanYear.startOf(2025).plusSeconds(3600));
        publishedWish(organizationA, "今年的願望");

        mvc.perform(as(get("/api/organizations/me/wishes").param("year", "2025"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("去年的願望"));

        mvc.perform(as(get("/api/organizations/me/wishes")
                        .param("year", String.valueOf(TaiwanYear.currentYear())), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("今年的願望"));
    }

    @Test
    @DisplayName("願望清單可同時用 year 與 status 篩選")
    void wishListFiltersByYearAndStatusTogether() throws Exception {
        UUID oldPublished = publishedWish(organizationA, "去年上架");
        backdateWish(oldPublished, TaiwanYear.startOf(2025).plusSeconds(3600));

        UUID oldDraft = wishes.save(Wish.draft(organizationA, "小星", AgeRange.AGE_7_9, "畫畫",
                "去年草稿", "描述", WishCategory.ART, PriceRange.UNDER_500)).getId();
        backdateWish(oldDraft, TaiwanYear.startOf(2025).plusSeconds(7200));

        mvc.perform(as(get("/api/organizations/me/wishes")
                        .param("year", "2025").param("status", "AVAILABLE"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("去年上架"));

        mvc.perform(as(get("/api/organizations/me/wishes")
                        .param("year", "2025").param("status", "DRAFT"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("去年草稿"));
    }

    @Test
    @DisplayName("願望年度篩選：台北 12/31 23:30 歸當年，即使 UTC 已跨年")
    void wishYearFilterRespectsTaipeiYearBoundary() throws Exception {
        UUID wishId = publishedWish(organizationA, "跨年邊界");
        // 台北時間 2025-12-31 23:30，UTC 是 2025-12-31 15:30，仍歸 2025 年
        Instant taipeiYearEnd = ZonedDateTime.of(2025, 12, 31, 23, 30, 0, 0, TaiwanYear.ZONE).toInstant();
        backdateWish(wishId, taipeiYearEnd);

        mvc.perform(as(get("/api/organizations/me/wishes").param("year", "2025"), ORG_A))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/organizations/me/wishes").param("year", "2026"), ORG_A))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("可選年份清單由本機構最早一筆願望的 createdAt 推導")
    void wishYearsEndpointDerivedFromEarliestCreatedAt() throws Exception {
        mvc.perform(as(get("/api/organizations/me/wishes/years"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(TaiwanYear.currentYear()));

        UUID oldWish = publishedWish(organizationA, "兩年前");
        int twoYearsAgo = TaiwanYear.currentYear() - 2;
        backdateWish(oldWish, TaiwanYear.startOf(twoYearsAgo).plusSeconds(3600));

        mvc.perform(as(get("/api/organizations/me/wishes/years"), ORG_A))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value(TaiwanYear.currentYear()))
                .andExpect(jsonPath("$[2]").value(twoYearsAgo));
    }

    @Test
    @DisplayName("願望年度篩選與可選年份都不會洩漏給其他機構")
    void wishYearFilterIsolatedPerOrganization() throws Exception {
        UUID wishId = publishedWish(organizationA, "甲機構去年的願望");
        backdateWish(wishId, TaiwanYear.startOf(2025).plusSeconds(3600));

        mvc.perform(as(get("/api/organizations/me/wishes").param("year", "2025"), ORG_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(as(get("/api/organizations/me/wishes/years"), ORG_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(TaiwanYear.currentYear()));
    }

    // ================================================================ 認領管理：claimedAt（cohort）

    @Test
    @DisplayName("認領清單以 claimedAt 的年度篩選（cohort 制）")
    void claimListFiltersByClaimedAtYear() throws Exception {
        UUID oldClaim = claimAs(publishedWish(organizationA, "去年認領"), DONOR);
        backdateClaim(oldClaim, TaiwanYear.startOf(2025).plusSeconds(3600));
        claimAs(publishedWish(organizationA, "今年認領"), DONOR);

        mvc.perform(as(get("/api/organizations/me/claims").param("year", "2025"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("去年認領"));

        mvc.perform(as(get("/api/organizations/me/claims")
                        .param("year", String.valueOf(TaiwanYear.currentYear())), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("今年認領"));
    }

    @Test
    @DisplayName("認領清單可同時用 year 與 status 篩選")
    void claimListFiltersByYearAndStatusTogether() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "去年待寄送"), DONOR);
        backdateClaim(claimId, TaiwanYear.startOf(2025).plusSeconds(3600));

        mvc.perform(as(get("/api/organizations/me/claims")
                        .param("year", "2025").param("status", "CLAIMED"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(as(get("/api/organizations/me/claims")
                        .param("year", "2025").param("status", "COMPLETED"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("認領年度篩選：台北 12/31 23:30 歸當年，即使 UTC 已跨年")
    void claimYearFilterRespectsTaipeiYearBoundary() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "跨年邊界"), DONOR);
        Instant taipeiYearEnd = ZonedDateTime.of(2025, 12, 31, 23, 30, 0, 0, TaiwanYear.ZONE).toInstant();
        backdateClaim(claimId, taipeiYearEnd);

        mvc.perform(as(get("/api/organizations/me/claims").param("year", "2025"), ORG_A))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/organizations/me/claims").param("year", "2026"), ORG_A))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("可選年份清單重用既有的 earliestClaimedAtByOrganization")
    void claimYearsEndpointReusesEarliestClaimedAt() throws Exception {
        mvc.perform(as(get("/api/organizations/me/claims/years"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(TaiwanYear.currentYear()));

        UUID oldClaim = claimAs(publishedWish(organizationA, "兩年前認領"), DONOR);
        int twoYearsAgo = TaiwanYear.currentYear() - 2;
        backdateClaim(oldClaim, TaiwanYear.startOf(twoYearsAgo).plusSeconds(3600));

        mvc.perform(as(get("/api/organizations/me/claims/years"), ORG_A))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value(TaiwanYear.currentYear()))
                .andExpect(jsonPath("$[2]").value(twoYearsAgo));
    }

    @Test
    @DisplayName("認領年度篩選與可選年份都不會洩漏給其他機構")
    void claimYearFilterIsolatedPerOrganization() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "甲機構去年認領"), DONOR);
        backdateClaim(claimId, TaiwanYear.startOf(2025).plusSeconds(3600));

        mvc.perform(as(get("/api/organizations/me/claims").param("year", "2025"), ORG_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(as(get("/api/organizations/me/claims/years"), ORG_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(TaiwanYear.currentYear()));
    }

    @Test
    @DisplayName("逾期提醒是待辦清單，不支援年度篩選——傳了 year 也不會被套用")
    void overdueListIgnoresYearParam() throws Exception {
        UUID claimId = claimAs(publishedWish(organizationA, "去年逾期未寄送"), DONOR);
        backdateClaim(claimId, TaiwanYear.startOf(2025).plusSeconds(3600));
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(3600 * 24)), claimId);
        entityManager.clear();

        // 逾期清單本來就不篩年度，即使跨年舊案（claimedAt 是 2025）也照樣出現在今天的逾期清單裡
        mvc.perform(as(get("/api/organizations/me/claims/overdue"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("去年逾期未寄送"));

        // 逾期清單的端點沒有宣告 year 參數，就算誤傳了也不影響結果——不是被忽略的隱藏行為，
        // 而是這條路徑上根本沒有 year 這回事
        mvc.perform(as(get("/api/organizations/me/claims/overdue").param("year", "2025"), ORG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
