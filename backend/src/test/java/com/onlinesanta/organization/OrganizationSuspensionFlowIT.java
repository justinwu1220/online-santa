package com.onlinesanta.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishFreezeCause;
import com.onlinesanta.wish.WishFreezeService;
import com.onlinesanta.wish.WishRepository;

import jakarta.persistence.EntityManager;

/**
 * 情境二：機構有冒名疑慮或違規行為。
 * 停權（保全證據、凍結願望與認領、通知）→ 機構仍可對話、改資料、補文件、申請解除，但不能對願望操作
 * → 管理員審核，復權只解除「機構停權」的凍結。
 */
@DisplayName("機構停權處置")
class OrganizationSuspensionFlowIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "susp-org@example.org";
    private static final String DONOR = "susp-donor@example.com";
    private static final String DONOR_SHIPPED = "susp-donor-shipped@example.com";

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired ClaimRepository claimRepository;
    @Autowired WishFreezeService freezes;
    @Autowired AdminAuditLogRepository auditLogs;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private Organization organization;
    private UUID availableWish;
    private UUID claimedWish;
    private UUID shippedWish;
    private UUID claimedClaim;
    private UUID shippedClaim;

    @BeforeEach
    void setUp() throws Exception {
        organization = Organization.register("停權測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.updateLegalInfo("王負責", "12345678");
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR_SHIPPED), DONOR_SHIPPED, "已寄出的民眾"));

        availableWish = publishedWish("可認領的");
        claimedWish = publishedWish("已認領的");
        shippedWish = publishedWish("已寄出的");
        claimedClaim = claim(claimedWish, DONOR);
        shippedClaim = claim(shippedWish, DONOR_SHIPPED);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, shippedClaim, DONOR_SHIPPED);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", shippedClaim), ShipRequests.of("郵局", "R1")), DONOR_SHIPPED))
                .andExpect(status().isOk());
        em.flush();
    }

    private UUID publishedWish(String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.saveAndFlush(wish).getId();
    }

    private UUID claim(UUID wishId, String donor) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void suspend() throws Exception {
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend", organization.getId()),
                        Map.of("note", "疑似冒名，先停權調查")), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("停權：保全原始資訊、公開中的願望全部凍結、未寄出的認領暫停期限、認領者看到機構停權說明")
    void suspendFreezesWishesAndPausesClaims() throws Exception {
        suspend();

        // 保全證據：停權當下的機構資料
        assertThat(jdbc.queryForObject(
                "select snapshot from suspension_records where target_id = ?", String.class, organization.getId()))
                .contains("停權測試機構").contains("王承辦");

        // 三個公開中的願望都因機構停權而凍結
        for (UUID wishId : new UUID[] {availableWish, claimedWish, shippedWish}) {
            Wish wish = wishes.findById(wishId).orElseThrow();
            assertThat(wish.isFrozen()).as(wish.getTitle()).isTrue();
            assertThat(wish.getFrozenCause()).isEqualTo(WishFreezeCause.ORG_SUSPENDED);
        }
        // 只有還沒寄出的認領暫停期限
        assertThat(claimRepository.findById(claimedClaim).orElseThrow().getDeadlinePausedAt()).isNotNull();
        assertThat(claimRepository.findById(shippedClaim).orElseThrow().getDeadlinePausedAt()).isNull();

        // 認領者端：未寄出與已寄出的認領都標示暫停，原因是機構停權
        mvc.perform(as(get("/api/claims/{id}", claimedClaim), DONOR))
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.pauseReason").value("ORG_SUSPENDED"));
        mvc.perform(as(get("/api/claims/{id}", shippedClaim), DONOR_SHIPPED))
                .andExpect(jsonPath("$.pauseReason").value("ORG_SUSPENDED"));
        // 認領者不能寄出、傳訊
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimedClaim), Map.of("body", "你好")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_RESTRICTED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("此機構已被管理員暫時停權")));

        // 機構在團隊對話收到停權說明
        mvc.perform(as(get("/api/organizations/me/team-chat"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[-1:].body").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("疑似冒名"))));
        assertThat(auditLogs.findAll())
                .anySatisfy(log -> assertThat(log.getAction()).isEqualTo(AdminAuditAction.SUSPEND_ORGANIZATION));
    }

    @Test
    @DisplayName("停權期間：機構不能對願望操作、不能聯繫認領者，但仍可對話、改資料、補文件、申請解除；也不能再被認領")
    void suspendedOrganizationIsRestrictedButCanTalkToAdmin() throws Exception {
        suspend();

        // 不能對願望操作
        mvc.perform(as(withBody(post("/api/wishes"), Map.of("title", "新願望")), ORG_USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_SUSPENDED"));
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimedClaim),
                        Map.of("reason", "OTHER")), ORG_USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_SUSPENDED"));
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", shippedClaim),
                        Map.of("inspected", true)), ORG_USER))
                .andExpect(status().isForbidden());
        // 不能聯繫認領者
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimedClaim), Map.of("body", "請寄給我")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_RESTRICTED"));
        // 願望牆上看不到，也不能再被認領
        mvc.perform(get("/api/wishes/{id}", availableWish)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/wishes/{id}/claim", availableWish), DONOR)).andExpect(status().isNotFound());

        // 仍可：看自己的願望、與管理員對話、改機構資料
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", claimedWish), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wish.freezeCause").value("ORG_SUSPENDED"))
                .andExpect(jsonPath("$.activeClaim.restriction").value("ORG_SUSPENDED"));
        mvc.perform(as(withBody(post("/api/organizations/me/team-chat"), Map.of("body", "我們會補資料")), ORG_USER))
                .andExpect(status().isCreated());
        mvc.perform(as(withBody(patch("/api/organizations/me"), updateRequest("新的承辦人")), ORG_USER))
                .andExpect(status().isOk());

        // 修改被記成修訂（欄位舊→新），不覆蓋停權當下的快照
        em.flush();
        assertThat(jdbc.queryForObject(
                "select changes from organization_revisions where organization_id = ?", String.class, organization.getId()))
                .contains("contactPerson").contains("王承辦").contains("新的承辦人");
        assertThat(jdbc.queryForObject(
                "select snapshot from suspension_records where target_id = ?", String.class, organization.getId()))
                .contains("王承辦").doesNotContain("新的承辦人");

        // 申請解除停權：同時只能有一筆
        mvc.perform(as(withBody(post("/api/organizations/me/unlock-request"), Map.of("message", "資料已補齊")), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(as(withBody(post("/api/organizations/me/unlock-request"), Map.of("message", "再一次")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UNLOCK_ALREADY_REQUESTED"));
    }

    private Map<String, Object> updateRequest(String contactPerson) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("name", "停權測試機構");
        request.put("contactPerson", contactPerson);
        request.put("contactEmail", "contact@example.org");
        request.put("mobilePhone", "0912-345-678");
        request.put("address", "台北市中正區某路 1 號");
        request.put("preferredDeliveryTime", "平日上午");
        request.put("description", "機構簡介");
        request.put("releasePolicy", "MANUAL");
        request.put("responsiblePerson", "王負責");
        request.put("withholdingTaxId", "12345678");
        return request;
    }

    @Test
    @DisplayName("管理員看得到處置資料；退回申請維持停權；復權只解除機構停權的凍結，其他原因的凍結保留，期限順延")
    void adminReviewsAndReactivatesWithoutLiftingOtherFreezes() throws Exception {
        // 已認領的願望本來就因內容疑慮被凍結
        Wish claimed = wishes.findById(claimedWish).orElseThrow();
        freezes.freeze(claimed, WishFreezeCause.ADMIN_CONTENT, "內容疑慮", null);
        em.flush();
        suspend();
        mvc.perform(as(withBody(post("/api/organizations/me/unlock-request"), Map.of("message", "資料已補齊")), ORG_USER))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/admin/organizations/{id}/suspension", organization.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("疑似冒名，先停權調查"))
                .andExpect(jsonPath("$.snapshot.name").value("停權測試機構"))
                .andExpect(jsonPath("$.snapshot.responsiblePerson").value("王負責"))
                .andExpect(jsonPath("$.snapshot.withholdingTaxId").value("12345678"))
                .andExpect(jsonPath("$.unlockRequest.status").value("PENDING"))
                .andExpect(jsonPath("$.openWishes").value(3))
                .andExpect(jsonPath("$.affectedClaims").value(2));

        // 退回：維持停權，機構在團隊對話看得到原因，可再申請
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/unlock-request/reject", organization.getId()),
                        Map.of("note", "還缺立案證書")), ADMIN))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/organizations/me/unlock-request"), ORG_USER))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("還缺立案證書"));
        mvc.perform(as(withBody(post("/api/organizations/me/unlock-request"), Map.of("message", "已補")), ORG_USER))
                .andExpect(status().isOk());

        // 復權
        em.flush();
        em.clear();
        mvc.perform(as(post("/api/admin/organizations/{id}/reactivate", organization.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        em.flush();
        em.clear();

        // 被內容疑慮凍結的願望仍然凍結（原因回到管理員的），其他的解除
        Wish stillFrozen = wishes.findById(claimedWish).orElseThrow();
        assertThat(stillFrozen.isFrozen()).isTrue();
        assertThat(stillFrozen.getFrozenCause()).isEqualTo(WishFreezeCause.ADMIN_CONTENT);
        assertThat(claimRepository.findById(claimedClaim).orElseThrow().getDeadlinePausedAt()).isNotNull();
        for (UUID wishId : new UUID[] {availableWish, shippedWish}) {
            assertThat(wishes.findById(wishId).orElseThrow().isFrozen()).isFalse();
        }
        mvc.perform(get("/api/wishes/{id}", availableWish)).andExpect(status().isOk());
        mvc.perform(as(get("/api/organizations/me/unlock-request"), ORG_USER))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        // 機構恢復後可以正常操作
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", shippedClaim), Map.of("body", "收到了")), ORG_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("管理員通知受影響的認領者：進行中的認領（含已寄出）都通知，並寫稽核")
    void adminNotifiesAffectedDonors() throws Exception {
        suspend();
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/notify-donors", organization.getId()),
                        Map.of("note", "該機構疑似冒名，請先不要再寄任何東西，我們正在處理")), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notified").value(2));
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.NOTIFY_AFFECTED_DONORS);
            assertThat(log.getTargetId()).isEqualTo(organization.getId());
        });
    }

    @Test
    @DisplayName("只有已核准的機構能被停權；只有已停權的能復權；非管理員不行")
    void suspendAndReactivateGuards() throws Exception {
        mvc.perform(as(post("/api/admin/organizations/{id}/reactivate", organization.getId()), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_SUSPENDED"));
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend", organization.getId()),
                        Map.of("note", "我不是管理員")), ORG_USER))
                .andExpect(status().isForbidden());
        suspend();
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend", organization.getId()),
                        Map.of("note", "再停權一次")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_APPROVED"));
    }
}
