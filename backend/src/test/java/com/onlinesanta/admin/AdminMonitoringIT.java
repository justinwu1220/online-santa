package com.onlinesanta.admin;

import static org.assertj.core.api.Assertions.assertThat;
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

@DisplayName("監控中心")
class AdminMonitoringIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
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
    AdminAuditLogRepository auditLogs;

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
        Organization org = Organization.register(name, "王承辦", "contact@example.org", null, null, null);
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

    private UUID claim(UUID wishId) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void backdateClaim(UUID claimId, Instant claimedAt) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET claimed_at = ? WHERE id = ?", Timestamp.from(claimedAt), claimId);
        entityManager.clear();
    }

    private void makeOverdue(UUID claimId) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), claimId);
        entityManager.clear();
    }

    private void backdateWish(UUID wishId, Instant createdAt) {
        entityManager.flush();
        jdbc.update("UPDATE wishes SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), wishId);
        entityManager.clear();
    }

    // ------------------------------------------------------------ 統計

    @Test
    @DisplayName("統計數字反映實際狀態")
    void statsReflectTheActualState() throws Exception {
        publishedWish(organizationA, "甲的願望一");
        publishedWish(organizationA, "甲的願望二");
        claim(publishedWish(organizationB, "乙的願望"));
        organizations.save(Organization.register("待審之家", "王承辦", "pending@example.org", null, null, null));

        mvc.perform(as(get("/api/admin/stats"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations.APPROVED").value(2))
                .andExpect(jsonPath("$.organizations.PENDING").value(1))
                .andExpect(jsonPath("$.pendingOrganizations").value(1))
                .andExpect(jsonPath("$.wishes.AVAILABLE").value(2))
                .andExpect(jsonPath("$.wishes.CLAIMED").value(1))
                .andExpect(jsonPath("$.availableWishes").value(2))
                .andExpect(jsonPath("$.claims.CLAIMED").value(1))
                .andExpect(jsonPath("$.overdueClaims").value(0));
    }

    @Test
    @DisplayName("沒有資料的狀態補成 0，而不是消失")
    void absentStatesAreReportedAsZero() throws Exception {
        // 前端要能穩定畫出完整分佈，不必處理「這個狀態這次不見了」
        mvc.perform(as(get("/api/admin/stats"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishes.DRAFT").value(0))
                .andExpect(jsonPath("$.wishes.FULFILLED").value(0))
                .andExpect(jsonPath("$.claims.COMPLETED").value(0))
                .andExpect(jsonPath("$.claims.RELEASED").value(0))
                .andExpect(jsonPath("$.organizations.SUSPENDED").value(0));
    }

    // ------------------------------------------------------------ 跨機構檢視

    @Test
    @DisplayName("願望清單涵蓋所有機構")
    void wishListSpansAllOrganizations() throws Exception {
        publishedWish(organizationA, "甲的願望");
        publishedWish(organizationB, "乙的願望");

        mvc.perform(as(get("/api/admin/wishes"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].organizationName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("甲機構", "乙機構")));
    }

    @Test
    @DisplayName("願望清單含草稿——機構自己看不到別家的，管理員全都看得到")
    void wishListIncludesDrafts() throws Exception {
        wishes.save(Wish.draft(organizationA, "小星", AgeRange.AGE_7_9, null,
                "草稿", null, WishCategory.TOY, PriceRange.UNDER_500));

        mvc.perform(as(get("/api/admin/wishes").param("status", "DRAFT"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ------------------------------------------------------------ 願望的年度篩選

    @Test
    @DisplayName("願望清單可用 year 篩選，以 createdAt 的台北日曆年為準")
    void wishListFiltersByYear() throws Exception {
        UUID wish2025 = publishedWish(organizationA, "去年的願望");
        backdateWish(wish2025, TaiwanYear.startOf(2025).plusSeconds(3600));

        UUID wish2026 = publishedWish(organizationA, "今年的願望");
        backdateWish(wish2026, TaiwanYear.startOf(2026).plusSeconds(3600));

        mvc.perform(as(get("/api/admin/wishes").param("year", "2025"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("去年的願望"));

        mvc.perform(as(get("/api/admin/wishes").param("year", "2026"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("今年的願望"));

        // 不帶 year 就是全部
        mvc.perform(as(get("/api/admin/wishes"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("願望清單的 status 與 year 篩選可以同時使用")
    void wishListFiltersByStatusAndYearTogether() throws Exception {
        UUID availableThisYear = publishedWish(organizationA, "今年上架中");
        backdateWish(availableThisYear, TaiwanYear.startOf(2026).plusSeconds(3600));

        UUID draftThisYear = wishes.save(Wish.draft(organizationA, "小星", AgeRange.AGE_7_9, null,
                "今年的草稿", null, WishCategory.TOY, PriceRange.UNDER_500)).getId();
        backdateWish(draftThisYear, TaiwanYear.startOf(2026).plusSeconds(7200));

        UUID availableLastYear = publishedWish(organizationA, "去年上架中");
        backdateWish(availableLastYear, TaiwanYear.startOf(2025).plusSeconds(3600));

        mvc.perform(as(get("/api/admin/wishes")
                        .param("status", "AVAILABLE").param("year", "2026"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("今年上架中"));

        // 只 status：兩個年度的上架中都算
        mvc.perform(as(get("/api/admin/wishes").param("status", "AVAILABLE"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(2));

        // 只 year：該年不分狀態都算
        mvc.perform(as(get("/api/admin/wishes").param("year", "2026"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(2));

        // 都不帶：全部三筆
        mvc.perform(as(get("/api/admin/wishes"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("年度邊界：台北 12/31 23:30 建立的願望歸當年，即使 UTC 已經是隔年")
    void wishYearFilterRespectsTaipeiYearBoundary() throws Exception {
        UUID wishId = publishedWish(organizationA, "跨年夜建立的願望");

        // 台北時間 2025-12-31 23:30，UTC 已經是 2026-01-01 15:30
        Instant taipeiNewYearsEve = ZonedDateTime
                .of(2025, 12, 31, 23, 30, 0, 0, TaiwanYear.ZONE)
                .toInstant();
        backdateWish(wishId, taipeiNewYearsEve);

        mvc.perform(as(get("/api/admin/wishes").param("year", "2025"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/admin/wishes").param("year", "2026"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("/api/admin/stats 附上願望的可選年份，供年度篩選下拉使用")
    void statsIncludesAvailableWishYears() throws Exception {
        UUID wish2024 = publishedWish(organizationA, "很久以前的願望");
        backdateWish(wish2024, TaiwanYear.startOf(2024).plusSeconds(3600));

        mvc.perform(as(get("/api/admin/stats"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableWishYears[0]").value(TaiwanYear.currentYear()))
                .andExpect(jsonPath("$.availableWishYears[" + (TaiwanYear.currentYear() - 2024) + "]")
                        .value(2024));
    }

    @Test
    @DisplayName("認領清單涵蓋所有機構並含捐贈者聯絡資訊")
    void claimListSpansAllOrganizations() throws Exception {
        claim(publishedWish(organizationA, "甲的願望"));

        mvc.perform(as(get("/api/admin/claims"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].organizationName").value("甲機構"))
                .andExpect(jsonPath("$.content[0].donorEmail").value(DONOR));
    }

    // ------------------------------------------------------------ 認領的年度篩選

    @Test
    @DisplayName("認領清單可用 year 篩選，以 claimedAt 的台北日曆年為準（cohort 口徑）")
    void claimListFiltersByYear() throws Exception {
        UUID claim2025 = claim(publishedWish(organizationA, "去年的認領"));
        backdateClaim(claim2025, TaiwanYear.startOf(2025).plusSeconds(3600));

        UUID claim2026 = claim(publishedWish(organizationA, "今年的認領"));
        backdateClaim(claim2026, TaiwanYear.startOf(2026).plusSeconds(3600));

        mvc.perform(as(get("/api/admin/claims").param("year", "2025"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("去年的認領"));

        mvc.perform(as(get("/api/admin/claims").param("year", "2026"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("今年的認領"));

        mvc.perform(as(get("/api/admin/claims"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("認領清單的 status 與 year 篩選可以同時使用")
    void claimListFiltersByStatusAndYearTogether() throws Exception {
        UUID claimedThisYear = claim(publishedWish(organizationA, "今年待寄送"));
        backdateClaim(claimedThisYear, TaiwanYear.startOf(2026).plusSeconds(3600));

        UUID cancelledThisYear = claim(publishedWish(organizationA, "今年已取消"));
        backdateClaim(cancelledThisYear, TaiwanYear.startOf(2026).plusSeconds(7200));
        mvc.perform(as(post("/api/claims/{id}/cancel", cancelledThisYear), DONOR))
                .andExpect(status().isOk());

        UUID claimedLastYear = claim(publishedWish(organizationA, "去年待寄送"));
        backdateClaim(claimedLastYear, TaiwanYear.startOf(2025).plusSeconds(3600));

        mvc.perform(as(get("/api/admin/claims")
                        .param("status", "CLAIMED").param("year", "2026"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("今年待寄送"));

        // 只 status：兩個年度的 CLAIMED 都算
        mvc.perform(as(get("/api/admin/claims").param("status", "CLAIMED"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(2));

        // 只 year：該年不分狀態都算
        mvc.perform(as(get("/api/admin/claims").param("year", "2026"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("逾期清單也能加 year 篩選")
    void overdueListFiltersByYearToo() throws Exception {
        UUID overdueThisYear = claim(publishedWish(organizationA, "今年逾期"));
        backdateClaim(overdueThisYear, TaiwanYear.startOf(2026).plusSeconds(3600));
        makeOverdue(overdueThisYear);

        UUID overdueLastYear = claim(publishedWish(organizationA, "去年逾期"));
        backdateClaim(overdueLastYear, TaiwanYear.startOf(2025).plusSeconds(3600));
        makeOverdue(overdueLastYear);

        mvc.perform(as(get("/api/admin/claims")
                        .param("overdue", "true").param("year", "2026"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("今年逾期"));

        // 只 overdue：兩個年度的逾期都算
        mvc.perform(as(get("/api/admin/claims").param("overdue", "true"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("認領年度邊界：台北 12/31 23:30 認領歸當年，即使 UTC 已經是隔年")
    void claimYearFilterRespectsTaipeiYearBoundary() throws Exception {
        UUID claimId = claim(publishedWish(organizationA, "跨年夜認領"));

        Instant taipeiNewYearsEve = ZonedDateTime
                .of(2025, 12, 31, 23, 30, 0, 0, TaiwanYear.ZONE)
                .toInstant();
        backdateClaim(claimId, taipeiNewYearsEve);

        mvc.perform(as(get("/api/admin/claims").param("year", "2025"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/admin/claims").param("year", "2026"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("/api/admin/stats 附上認領的可選年份，供年度篩選下拉使用")
    void statsIncludesAvailableClaimYears() throws Exception {
        UUID claim2023 = claim(publishedWish(organizationA, "很久以前的認領"));
        backdateClaim(claim2023, TaiwanYear.startOf(2023).plusSeconds(3600));

        mvc.perform(as(get("/api/admin/stats"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableClaimYears[0]").value(TaiwanYear.currentYear()))
                .andExpect(jsonPath("$.availableClaimYears[" + (TaiwanYear.currentYear() - 2023) + "]")
                        .value(2023));
    }

    // ------------------------------------------------------------ 稽核

    @Test
    @DisplayName("清單頁不寫稽核")
    void listingDoesNotGenerateAuditNoise() throws Exception {
        claim(publishedWish(organizationA, "願望"));

        mvc.perform(as(get("/api/admin/wishes"), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(get("/api/admin/claims"), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(get("/api/admin/stats"), ADMIN)).andExpect(status().isOk());

        assertThat(auditLogs.count())
                .as("每次翻頁都記，會把真正重要的紀錄淹沒")
                .isZero();
    }

    @Test
    @DisplayName("打開單筆認領詳情會寫稽核")
    void viewingAClaimIsAudited() throws Exception {
        UUID claimId = claim(publishedWish(organizationA, "被檢視的願望"));

        mvc.perform(as(get("/api/admin/claims/{id}", claimId), ADMIN))
                .andExpect(status().isOk());

        var logs = auditLogs.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AdminAuditAction.VIEW_CLAIM_DETAIL);
        assertThat(logs.get(0).getTargetType()).isEqualTo(AdminAuditTargetType.CLAIM);
        assertThat(logs.get(0).getTargetId()).isEqualTo(claimId);
        assertThat(logs.get(0).getDetail()).contains("甲機構", "被檢視的願望");
    }

    @Test
    @DisplayName("看附件會寫稽核，並記下看到幾個檔案")
    void viewingAttachmentsIsAudited() throws Exception {
        UUID claimId = claim(publishedWish(organizationA, "有附件的願望"));

        mvc.perform(as(get("/api/admin/claims/{id}/attachments", claimId), ADMIN))
                .andExpect(status().isOk());

        var logs = auditLogs.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AdminAuditAction.VIEW_CLAIM_ATTACHMENTS);
        assertThat(logs.get(0).getDetail()).contains("共 0 個檔案");
    }

    @Test
    @DisplayName("審核決定會寫稽核")
    void reviewDecisionsAreAudited() throws Exception {
        Organization pending = organizations.save(
                Organization.register("待審之家", "王承辦", "pending@example.org", null, null, null));

        mvc.perform(as(post("/api/admin/organizations/{id}/approve", pending.getId()), ADMIN))
                .andExpect(status().isOk());

        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(AdminAuditAction.APPROVE_ORGANIZATION);
                    assertThat(log.getDetail()).isEqualTo("待審之家");
                });
    }

    @Test
    @DisplayName("觸發排程會寫稽核")
    void runningTheSweepIsAudited() throws Exception {
        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), ADMIN))
                .andExpect(status().isOk());

        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(AdminAuditAction.RUN_RELEASE_SWEEP);
                    assertThat(log.getTargetType()).isEqualTo(AdminAuditTargetType.SYSTEM);
                    assertThat(log.getTargetId()).isNull();
                });
    }

    @Test
    @DisplayName("稽核軌跡可被查詢，且看得到是哪位管理員")
    void auditTrailIsQueryable() throws Exception {
        UUID claimId = claim(publishedWish(organizationA, "願望"));
        mvc.perform(as(get("/api/admin/claims/{id}", claimId), ADMIN)).andExpect(status().isOk());

        mvc.perform(as(get("/api/admin/audit-logs"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("VIEW_CLAIM_DETAIL"))
                .andExpect(jsonPath("$.content[0].adminEmail").value(ADMIN));
    }

    @Test
    @DisplayName("稽核軌跡可依動作篩選")
    void auditTrailCanBeFilteredByAction() throws Exception {
        UUID claimId = claim(publishedWish(organizationA, "願望"));
        mvc.perform(as(get("/api/admin/claims/{id}", claimId), ADMIN));
        mvc.perform(as(get("/api/admin/claims/{id}/attachments", claimId), ADMIN));

        mvc.perform(as(get("/api/admin/audit-logs")
                        .param("action", "VIEW_CLAIM_ATTACHMENTS"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ------------------------------------------------------------ 權限

    @Test
    @DisplayName("非管理員一律擋下")
    void nonAdminsAreRejectedEverywhere() throws Exception {
        UUID claimId = claim(publishedWish(organizationA, "願望"));

        for (String path : new String[]{
                "/api/admin/stats", "/api/admin/audit-logs",
                "/api/admin/wishes", "/api/admin/claims"}) {
            mvc.perform(as(get(path), DONOR)).andExpect(status().isForbidden());
            mvc.perform(as(get(path), ORG_A)).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }

        // 機構成員也不能繞過自己的機構範圍去看別家的認領
        mvc.perform(as(get("/api/admin/claims/{id}", claimId), ORG_B))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("被擋下的存取不會留下稽核紀錄")
    void rejectedAccessLeavesNoAuditTrail() throws Exception {
        UUID claimId = claim(publishedWish(organizationA, "願望"));

        mvc.perform(as(get("/api/admin/claims/{id}", claimId), DONOR))
                .andExpect(status().isForbidden());

        assertThat(auditLogs.count())
                .as("記錄的是實際發生的存取，不是被擋下的嘗試")
                .isZero();
    }
}
