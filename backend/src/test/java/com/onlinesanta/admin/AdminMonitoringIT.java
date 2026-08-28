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

    private Organization organizationA;
    private Organization organizationB;

    @BeforeEach
    void setUp() {
        organizationA = approvedOrganization("甲機構", ORG_A);
        organizationB = approvedOrganization("乙機構", ORG_B);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private Organization approvedOrganization(String name, String memberEmail) {
        Organization org = Organization.register(name, "contact@example.org", null, null, null);
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

    // ------------------------------------------------------------ 統計

    @Test
    @DisplayName("統計數字反映實際狀態")
    void statsReflectTheActualState() throws Exception {
        publishedWish(organizationA, "甲的願望一");
        publishedWish(organizationA, "甲的願望二");
        claim(publishedWish(organizationB, "乙的願望"));
        organizations.save(Organization.register("待審之家", "pending@example.org", null, null, null));

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
                Organization.register("待審之家", "pending@example.org", null, null, null));

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
