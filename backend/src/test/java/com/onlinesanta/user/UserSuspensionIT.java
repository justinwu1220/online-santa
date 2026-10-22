package com.onlinesanta.user;

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
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishFreezeCause;
import com.onlinesanta.wish.WishRepository;
import com.onlinesanta.wish.WishStatus;

import jakarta.persistence.EntityManager;

/**
 * 情境三：認領者出現違規行為。
 * 停權（受限登入：可讀取、寫入全擋）→ 進行中的認領連動凍結、機構後台標示 → 管理員釋出認領或解除停權。
 */
@DisplayName("認領者停權處置")
class UserSuspensionIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "ban-org@example.org";
    private static final String DONOR = "ban-donor@example.com";
    private static final String BYSTANDER = "ban-bystander@example.com";

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired ClaimRepository claimRepository;
    @Autowired AdminAuditLogRepository auditLogs;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private Organization organization;
    private UUID donorId;
    private UUID unshippedWish;
    private UUID shippedWish;
    private UUID unshippedClaim;
    private UUID shippedClaim;

    @BeforeEach
    void setUp() throws Exception {
        organization = Organization.register("認領者違規測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        donorId = users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "違規的民眾")).getId();
        users.save(User.newDonor(TestJwtSupport.uidFor(BYSTANDER), BYSTANDER, "路過的民眾"));

        unshippedWish = publishedWish("還沒寄的");
        shippedWish = publishedWish("已經寄的");
        unshippedClaim = claim(unshippedWish);
        shippedClaim = claim(shippedWish);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, shippedClaim, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", shippedClaim), ShipRequests.of("郵局", "R1")), DONOR))
                .andExpect(status().isOk());
        em.flush();
    }

    private UUID publishedWish(String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.saveAndFlush(wish).getId();
    }

    private UUID claim(UUID wishId) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void suspendDonor() throws Exception {
        mvc.perform(as(withBody(post("/api/admin/users/{id}/suspend", donorId),
                        Map.of("note", "對機構騷擾，先停權處理")), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true));
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("受限登入：可以登入讀取並看到停權通知，所有寫入動作被擋")
    void suspendedDonorCanReadButNotWrite() throws Exception {
        suspendDonor();

        mvc.perform(as(get("/api/me"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.suspendedReason").value("對機構騷擾，先停權處理"));
        mvc.perform(as(get("/api/claims/me"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        UUID another = publishedWish("想再認領的");
        mvc.perform(as(post("/api/wishes/{id}/claim", another), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_SUSPENDED"));
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", unshippedClaim), Map.of("body", "你好")), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_SUSPENDED"));
        mvc.perform(as(withBody(patch("/api/me/profile"), Map.of("displayName", "新名字")), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_SUSPENDED"));
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", unshippedClaim), Map.of("reason", "不想送")), DONOR))
                .andExpect(status().isForbidden());

        // 沒有被停權的人完全不受影響
        mvc.perform(as(get("/api/me"), BYSTANDER)).andExpect(jsonPath("$.suspended").value(false));
        mvc.perform(as(post("/api/wishes/{id}/claim", another), BYSTANDER)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("進行中的認領連動凍結：認領者看到限制、機構後台標示並提示，機構不能收回或傳訊但仍可確認收到實體禮物")
    void claimsAreFrozenAndOrganizationSeesTheNotice() throws Exception {
        suspendDonor();

        for (UUID wishId : new UUID[] {unshippedWish, shippedWish}) {
            Wish wish = wishes.findById(wishId).orElseThrow();
            assertThat(wish.getFrozenCause()).isEqualTo(WishFreezeCause.DONOR_SUSPENDED);
        }
        assertThat(claimRepository.findById(unshippedClaim).orElseThrow().getDeadlinePausedAt()).isNotNull();
        assertThat(claimRepository.findById(shippedClaim).orElseThrow().getDeadlinePausedAt()).isNull();

        mvc.perform(as(get("/api/claims/{id}", unshippedClaim), DONOR))
                .andExpect(jsonPath("$.pauseReason").value("DONOR_SUSPENDED"));

        mvc.perform(as(get("/api/organizations/me/wishes/{id}", unshippedWish), ORG_USER))
                .andExpect(jsonPath("$.wish.frozen").value(true))
                .andExpect(jsonPath("$.wish.freezeCause").value("DONOR_SUSPENDED"))
                .andExpect(jsonPath("$.activeClaim.restriction").value("DONOR_SUSPENDED"))
                .andExpect(jsonPath("$.activeClaim.restrictionMessage").value("認領者出現違規行為，正在積極處理中。"));

        // 機構不能收回、不能傳訊
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", unshippedClaim),
                        Map.of("reason", "OTHER")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_RESTRICTED"));
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", unshippedClaim), Map.of("body", "請聯絡我")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_RESTRICTED"));

        // 已寄出的：機構仍可確認收到實體禮物
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, shippedClaim, ORG_USER);
        completeAsOrganization(shippedClaim, ORG_USER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("管理員釋出還沒寄出的認領：願望回到願望牆、認領者仍停權、已寄出的認領不能釋出")
    void adminReleasesAnUnshippedClaim() throws Exception {
        suspendDonor();

        mvc.perform(as(get("/api/admin/users/{id}", donorId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(DONOR))
                .andExpect(jsonPath("$.snapshot.email").value(DONOR))
                .andExpect(jsonPath("$.claims.length()").value(2))
                .andExpect(jsonPath("$.claims[?(@.claimId=='" + unshippedClaim + "')].canRelease").value(true))
                .andExpect(jsonPath("$.claims[?(@.claimId=='" + shippedClaim + "')].canRelease").value(false));

        mvc.perform(as(withBody(post("/api/admin/claims/{id}/release", shippedClaim), Map.of("note", "試試")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_NOT_RELEASABLE"));

        mvc.perform(as(withBody(post("/api/admin/claims/{id}/release", unshippedClaim),
                        Map.of("note", "認領者違規，釋出讓其他人認領")), ADMIN))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        Wish wish = wishes.findById(unshippedWish).orElseThrow();
        assertThat(wish.getStatus()).isEqualTo(WishStatus.AVAILABLE);
        assertThat(wish.isFrozen()).isFalse();
        mvc.perform(get("/api/wishes/{id}", unshippedWish)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from claim_events where claim_id = ? and event_type = 'RELEASED_ADMIN'",
                Integer.class, unshippedClaim)).isEqualTo(1);
        assertThat(users.findById(donorId).orElseThrow().isSuspended()).isTrue();
        assertThat(auditLogs.findAll())
                .anySatisfy(log -> assertThat(log.getAction()).isEqualTo(AdminAuditAction.RELEASE_CLAIM_BY_ADMIN));
    }

    @Test
    @DisplayName("解除停權：功能恢復，因停權而凍結的願望解除、期限順延，保留的認領繼續")
    void reinstateRestoresEverything() throws Exception {
        suspendDonor();

        mvc.perform(as(withBody(post("/api/admin/users/{id}/reactivate", donorId), Map.of("note", "評估無風險")), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(false));
        em.flush();
        em.clear();

        for (UUID wishId : new UUID[] {unshippedWish, shippedWish}) {
            assertThat(wishes.findById(wishId).orElseThrow().isFrozen()).isFalse();
        }
        assertThat(claimRepository.findById(unshippedClaim).orElseThrow().getDeadlinePausedAt()).isNull();
        mvc.perform(as(get("/api/me"), DONOR)).andExpect(jsonPath("$.suspended").value(false));
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", unshippedClaim), Map.of("body", "我回來了")), DONOR))
                .andExpect(status().isOk());
        assertThat(auditLogs.findAll())
                .anySatisfy(log -> assertThat(log.getAction()).isEqualTo(AdminAuditAction.REACTIVATE_USER));
    }

    @Test
    @DisplayName("停權的限制：只有一般認領者能被停權、不能重複停權、解除要先停權、非管理員不行；可搜尋認領者")
    void guardsAndSearch() throws Exception {
        UUID orgMemberId = users.findByEmailIgnoreCase(ORG_USER).orElseThrow().getId();
        mvc.perform(as(withBody(post("/api/admin/users/{id}/suspend", orgMemberId), Map.of("note", "不該成功")), ADMIN))
                .andExpect(status().isNotFound());
        mvc.perform(as(withBody(post("/api/admin/users/{id}/suspend", donorId), Map.of("note", "我不是管理員")), BYSTANDER))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/users/{id}/reactivate", donorId), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_SUSPENDED"));

        suspendDonor();
        mvc.perform(as(withBody(post("/api/admin/users/{id}/suspend", donorId), Map.of("note", "再停權一次")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_SUSPENDED"));

        mvc.perform(as(get("/api/admin/users").param("q", "ban-donor"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].suspended").value(true));
    }
}
