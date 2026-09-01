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

import com.onlinesanta.admin.dto.ReviewDecisionRequest;
import com.onlinesanta.admin.dto.ReviewReasonRequest;
import com.onlinesanta.claim.dto.ShipRequest;
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
 * 管理端停權/復權機構——PRIVACY.md 資安事件應變手冊第一步的執行能力。
 */
@DisplayName("機構停權與復權")
class OrganizationSuspensionIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "suspend-target@example.org";
    private static final String DONOR = "suspend-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    AdminAuditLogRepository auditLogs;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = approvedOrganization("待測機構", ORG_USER);
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

    private UUID publishedWish(String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }

    private void suspend(UUID organizationId, String note) throws Exception {
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend", organizationId),
                        new ReviewReasonRequest(note)), ADMIN))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 公開曝光下架

    @Test
    @DisplayName("停權後，機構的願望立刻從願望牆與詳情頁下架")
    void suspendHidesOrganizationsWishesFromWallAndDetail() throws Exception {
        UUID wishId = publishedWish("停權前還看得到");

        mvc.perform(get("/api/wishes"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(status().isOk());

        suspend(organization.getId(), "接獲檢舉，暫停查核");

        mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 詳情回 404 而非洩漏「機構被停權」這個原因
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("復權後，願望重新出現在願望牆與詳情頁")
    void reactivateRestoresPublicVisibility() throws Exception {
        UUID wishId = publishedWish("會被恢復");
        suspend(organization.getId(), "查核中");

        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/reactivate", organization.getId()),
                        new ReviewDecisionRequest("查核完畢，恢復正常")), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/wishes"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("進行中的認領不受停權影響，寄送流程能繼續走完")
    void inProgressClaimsContinueAfterSuspension() throws Exception {
        UUID wishId = publishedWish("認領中的願望");
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(body).get("id").asText());

        suspend(organization.getId(), "查核中，但不影響已經在途的送禮");

        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        new ShipRequest("郵局", "R123")), DONOR))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/complete", claimId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ------------------------------------------------------------ 狀態轉換守門

    @Test
    @DisplayName("只有 APPROVED 的機構能被停權")
    void onlyApprovedOrganizationsCanBeSuspended() throws Exception {
        Organization pending = Organization.register(
                "待審機構", "王承辦", "pending@example.org", null, null, null);
        organizations.save(pending);

        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend", pending.getId()),
                        new ReviewReasonRequest("理由")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_APPROVED"));

        // 已經停權的機構不能再被停權一次
        suspend(organization.getId(), "第一次停權");
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend", organization.getId()),
                        new ReviewReasonRequest("第二次")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_APPROVED"));
    }

    @Test
    @DisplayName("停權的理由必填，空白或缺漏都會被擋下；復權的附註仍然選填")
    void suspensionRequiresANonBlankReasonButReactivationDoesNot() throws Exception {
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend", organization.getId()),
                        new ReviewReasonRequest("   ")), ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.note").exists());

        mvc.perform(as(post("/api/admin/organizations/{id}/suspend", organization.getId()), ADMIN))
                .andExpect(status().isBadRequest());

        suspend(organization.getId(), "接獲檢舉");

        // 復權不受影響，附註依然選填（不傳 body 也能過）
        mvc.perform(as(post("/api/admin/organizations/{id}/reactivate", organization.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("只有 SUSPENDED 的機構能被復權")
    void onlySuspendedOrganizationsCanBeReactivated() throws Exception {
        // 還是 APPROVED，沒被停權過
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/reactivate", organization.getId()),
                        new ReviewDecisionRequest("理由")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_SUSPENDED"));
    }

    // ------------------------------------------------------------ 稽核與授權

    @Test
    @DisplayName("停權與復權都會寫入稽核紀錄，附上理由")
    void suspensionAndReactivationAreAudited() throws Exception {
        suspend(organization.getId(), "接獲檢舉");
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/reactivate", organization.getId()),
                        new ReviewDecisionRequest("查證後排除疑慮")), ADMIN))
                .andExpect(status().isOk());

        var records = auditLogs.findAll();
        assertThat(records).hasSize(2);

        assertThat(records).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.SUSPEND_ORGANIZATION);
            assertThat(log.getTargetType()).isEqualTo(AdminAuditTargetType.ORGANIZATION);
            assertThat(log.getTargetId()).isEqualTo(organization.getId());
            assertThat(log.getDetail()).contains("接獲檢舉");
        });
        assertThat(records).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.REACTIVATE_ORGANIZATION);
            assertThat(log.getTargetId()).isEqualTo(organization.getId());
            assertThat(log.getDetail()).contains("查證後排除疑慮");
        });
    }

    @Test
    @DisplayName("非管理員不能停權或復權機構")
    void nonAdminsCannotSuspendOrReactivate() throws Exception {
        mvc.perform(as(post("/api/admin/organizations/{id}/suspend", organization.getId()), DONOR))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/organizations/{id}/suspend", organization.getId()), ORG_USER))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/organizations/{id}/suspend", organization.getId()))
                .andExpect(status().isUnauthorized());

        suspend(organization.getId(), "為了測復權授權，先合法停權一次");
        mvc.perform(as(post("/api/admin/organizations/{id}/reactivate", organization.getId()), DONOR))
                .andExpect(status().isForbidden());
    }
}
