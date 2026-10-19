package com.onlinesanta.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.organization.dto.OrganizationUpdateRequest;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

import jakarta.persistence.EntityManager;

@DisplayName("機構確認收到並完成、收回原因、自動收回的寬限天數")
class ClaimCompletionIT extends ApiIntegrationTest {

    private static final String ORG_USER = "done-org@example.org";
    private static final String AUTO_ORG_USER = "done-auto-org@example.org";
    private static final String DONOR = "done-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    private Organization organization;
    private Organization autoOrganization;

    @BeforeEach
    void setUp() {
        organization = organization("確認收到之家", ORG_USER, ReleasePolicy.MANUAL, null);
        autoOrganization = organization("寬限天數之家", AUTO_ORG_USER, ReleasePolicy.AUTO, 3);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private Organization organization(String name, String memberEmail, ReleasePolicy policy, Integer days) {
        Organization org = Organization.register(name, "王承辦", "contact@example.org", null, null, null);
        org.approve(null, "測試資料");
        org.updateReleasePolicy(policy, days);
        organizations.save(org);
        User member = User.newDonor(TestJwtSupport.uidFor(memberEmail), memberEmail, memberEmail);
        member.joinOrganization(org.getId());
        users.save(member);
        return org;
    }

    private UUID claim(Organization org, String title) throws Exception {
        Wish wish = Wish.draft(org, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述", WishCategory.ART,
                PriceRange.UNDER_500);
        wish.publish();
        UUID wishId = wishes.save(wish).getId();
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private UUID shippedClaim(String title) throws Exception {
        UUID claimId = claim(organization, title);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId), ShipRequests.of("郵局", "R123")), DONOR))
                .andExpect(status().isOk());
        return claimId;
    }

    private void complete(UUID claimId, Map<String, Object> body, String user,
                          org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId), body), user))
                .andExpect(expected);
    }

    // ------------------------------------------------------------ 確認收到並完成

    @Test
    @DisplayName("沒有收件證明照片不能完成；上傳後可以在寄出的當下就上傳")
    void completionNeedsAtLeastOneProofPhoto() throws Exception {
        UUID claimId = shippedClaim("要有證明");

        complete(claimId, Map.of("inspected", true), ORG_USER, status().isConflict());
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        Map.of("inspected", true)), ORG_USER))
                .andExpect(jsonPath("$.errorCode").value("FEEDBACK_PHOTO_REQUIRED"));

        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);
        complete(claimId, Map.of("inspected", true), ORG_USER, status().isOk());
    }

    @Test
    @DisplayName("收件證明在已認領（還沒寄出）時不能上傳")
    void proofCannotBeUploadedBeforeShipping() throws Exception {
        UUID claimId = claim(organization, "還沒寄出");

        var request = new com.onlinesanta.attachment.dto.UploadUrlRequest(
                AttachmentPurpose.ORG_FEEDBACK, claimId, "image/jpeg", 120_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("一定要勾「已拆封檢查」；實際收到日期不可是未來，也不可早於寄出日期")
    void inspectionAndDateRules() throws Exception {
        UUID claimId = shippedClaim("規則");
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        complete(claimId, Map.of("inspected", false), ORG_USER, status().isBadRequest());
        complete(claimId, Map.of(), ORG_USER, status().isBadRequest());

        String tomorrow = LocalDate.now(TaiwanYear.ZONE).plusDays(1).toString();
        complete(claimId, Map.of("inspected", true, "receivedOn", tomorrow), ORG_USER, status().isBadRequest());

        String beforeShipped = LocalDate.now(TaiwanYear.ZONE).minusDays(3).toString();
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        Map.of("inspected", true, "receivedOn", beforeShipped)), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECEIVED_BEFORE_SHIPPED"));

        // 狀態沒有被中間失敗的嘗試改動
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("完成時記下實際收到日期（和確認時間分開），認領者看得到")
    void receivedOnIsRecordedSeparatelyFromTheConfirmationTime() throws Exception {
        UUID claimId = shippedClaim("記錄日期");
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);
        String today = LocalDate.now(TaiwanYear.ZONE).toString();

        complete(claimId, Map.of("inspected", true, "receivedOn", today), ORG_USER, status().isOk());

        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.receivedOn").value(today))
                .andExpect(jsonPath("$.receivedAt").exists())
                .andExpect(jsonPath("$.completedAt").exists());
        mvc.perform(get("/api/wishes").param("status", "FULFILLED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("舊流程留下的「已收到、待完成」認領也能從同一個端點完成，原本的收到時間不變")
    void legacyReceivedClaimsCanStillBeCompleted() throws Exception {
        UUID claimId = shippedClaim("舊的已收到");
        entityManager.flush();
        jdbc.update("UPDATE claims SET status = 'RECEIVED', received_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)), claimId);
        entityManager.clear();
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        complete(claimId, Map.of("inspected", true), ORG_USER, status().isOk());

        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.receivedOn").doesNotExist());
    }

    // ------------------------------------------------------------ 收回

    @Test
    @DisplayName("收回原因四選一，選「其他」可以補寫說明；沒選或亂填會被擋下")
    void recallReasonIsOneOfFourChoices() throws Exception {
        UUID claimId = claim(organization, "要收回");

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId), Map.of()), ORG_USER))
                .andExpect(status().isBadRequest());
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        Map.of("reason", "因為我想")), ORG_USER))
                .andExpect(status().isBadRequest());

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        Map.of("reason", "OTHER", "detail", "孩子已轉到別的機構")), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releaseReason").value("其他：孩子已轉到別的機構"));
        // 收回後願望重新上架
        mvc.perform(get("/api/wishes").param("status", "AVAILABLE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ------------------------------------------------------------ 寬限天數

    @Test
    @DisplayName("自動收回的寬限天數只能是 1、3、5、7")
    void graceDaysAreOneOfFourChoices() throws Exception {
        for (int days : new int[] {2, 10, 0}) {
            var update = new OrganizationUpdateRequest("寬限天數之家", "王承辦", "contact@example.org",
                    null, "0912-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童",
                    ReleasePolicy.AUTO, days);
            // 2 不在四個選項裡（409）；0 與 10 超出範圍，驗證階段就擋下（400）
            var expected = days == 2 ? status().isConflict() : status().isBadRequest();
            mvc.perform(as(withBody(patch("/api/organizations/me"), update), AUTO_ORG_USER))
                    .andExpect(expected);
        }
        var ok = new OrganizationUpdateRequest("寬限天數之家", "王承辦", "contact@example.org",
                null, "0912-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童",
                ReleasePolicy.AUTO, 5);
        mvc.perform(as(withBody(patch("/api/organizations/me"), ok), AUTO_ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseAfterDays").value(5));
    }

    @Test
    @DisplayName("自動收回要過了「寄出期限＋寬限天數」：寬限內只標逾期，過了才收回")
    void autoReleaseWaitsForTheGracePeriod() throws Exception {
        UUID claimId = claim(autoOrganization, "寬限內");
        // 認領當下的寄出期限是全站固定天數，不是機構的寬限天數
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(status().isOk());
        Instant deadline = jdbc.queryForObject("SELECT ship_deadline_at FROM claims WHERE id = ?",
                Timestamp.class, claimId).toInstant();
        assertThat(ChronoUnit.DAYS.between(Instant.now(), deadline)).isBetween(5L, 7L);
        assertThat(jdbc.queryForObject("SELECT release_grace_days FROM claims WHERE id = ?",
                Integer.class, claimId)).isEqualTo(3);

        // 期限剛過 1 天：在 3 天寬限內，不收回
        setDeadline(claimId, Instant.now().minus(1, ChronoUnit.DAYS));
        sweep();
        assertThat(claimStatus(claimId)).isEqualTo("CLAIMED");

        // 期限已過 4 天：超過寬限，收回
        setDeadline(claimId, Instant.now().minus(4, ChronoUnit.DAYS));
        sweep();
        assertThat(claimStatus(claimId)).isEqualTo("RELEASED");
    }

    private void setDeadline(UUID claimId, Instant deadline) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?", Timestamp.from(deadline), claimId);
        entityManager.clear();
    }

    private String claimStatus(UUID claimId) {
        return jdbc.queryForObject("SELECT status FROM claims WHERE id = ?", String.class, claimId);
    }

    private void sweep() throws Exception {
        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), PLATFORM_ADMIN))
                .andExpect(status().isOk());
    }
}
