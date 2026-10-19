package com.onlinesanta.wish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

import jakarta.persistence.EntityManager;

@DisplayName("機構後台的合併讀取：願望加上它目前的認領")
class OrganizationWishViewIT extends ApiIntegrationTest {

    private static final String ORG_USER = "view-org@example.org";
    private static final String OTHER_ORG_USER = "view-other-org@example.org";

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

    @BeforeEach
    void setUp() {
        organization = approvedOrganization("合併讀取之家", ORG_USER);
        approvedOrganization("別家機構", OTHER_ORG_USER);
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

    private UUID draft(String title) {
        return wishes.save(Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述",
                WishCategory.ART, PriceRange.UNDER_500)).getId();
    }

    private UUID open(String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述",
                WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }

    /** 每位認領者同時最多 3 筆進行中的認領，所以每次認領換一位。 */
    private final Map<UUID, String> claimant = new HashMap<>();
    private int nextDonor = 0;

    private UUID claim(UUID wishId) throws Exception {
        String donor = "view-donor-" + (nextDonor++) + "@example.com";
        users.save(User.newDonor(TestJwtSupport.uidFor(donor), donor, "熱心民眾"));
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donor))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(body).get("id").asText());
        claimant.put(claimId, donor);
        return claimId;
    }

    private void ship(UUID claimId) throws Exception {
        String donor = claimant.get(claimId);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, donor);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId), ShipRequests.of("郵局", "R123")), donor))
                .andExpect(status().isOk());
    }

    private Map<String, String> stagesByTitle() throws Exception {
        String body = mvc.perform(as(get("/api/organizations/me/wishes").param("size", "50"), ORG_USER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, String> stages = new HashMap<>();
        for (JsonNode wish : json.readTree(body).get("content")) {
            stages.put(wish.get("title").asText(), wish.get("stage").asText());
        }
        return stages;
    }

    @Test
    @DisplayName("每個願望帶合併狀態：草稿、審核中、可認領、待寄出、逾期、已寄出、已完成")
    void everyWishCarriesItsMergedStage() throws Exception {
        draft("草稿");

        UUID pending = draft("審核中");
        uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER, pending, ORG_USER);
        mvc.perform(as(post("/api/wishes/{id}/submit", pending), ORG_USER)).andExpect(status().isOk());

        open("可認領");
        claim(open("待寄出"));

        UUID overdueClaim = claim(open("逾期"));
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), overdueClaim);
        entityManager.clear();

        ship(claim(open("已寄出")));

        UUID doneClaim = claim(open("已完成"));
        ship(doneClaim);
        completeAsOrganization(doneClaim, ORG_USER).andExpect(status().isOk());

        assertThat(stagesByTitle()).containsEntry("草稿", "DRAFT")
                .containsEntry("審核中", "PENDING_REVIEW")
                .containsEntry("可認領", "OPEN")
                .containsEntry("待寄出", "CLAIMED_WAITING")
                .containsEntry("逾期", "CLAIMED_OVERDUE")
                .containsEntry("已寄出", "SHIPPED")
                .containsEntry("已完成", "DONE");
    }

    @Test
    @DisplayName("清單的認領摘要不含認領者信箱與禮物價格，並帶未讀數")
    void activeClaimSummaryIsPrivacySafeAndCarriesUnread() throws Exception {
        UUID wishId = open("摘要");
        UUID claimId = claim(wishId);
        ship(claimId);

        String body = mvc.perform(as(get("/api/organizations/me/wishes"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].activeClaim.donorName").value("熱心民眾"))
                .andExpect(jsonPath("$.content[0].activeClaim.status").value("SHIPPED"))
                // 認領的系統通知 ＋ 回報寄送的系統通知
                .andExpect(jsonPath("$.content[0].unreadCount").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("view-donor-").doesNotContain("giftPrice").doesNotContain("donorEmail");
    }

    @Test
    @DisplayName("篩選列的數量：各狀態、已認領的細項、逾期")
    void stageCounts() throws Exception {
        draft("草稿一");
        draft("草稿二");
        open("可認領一");
        claim(open("待寄出一"));
        UUID overdueClaim = claim(open("逾期一"));
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), overdueClaim);
        entityManager.clear();
        ship(claim(open("已寄出一")));

        mvc.perform(as(get("/api/organizations/me/wishes/stage-counts"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.all").value(6))
                .andExpect(jsonPath("$.draft").value(2))
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.claimed").value(3))
                .andExpect(jsonPath("$.claimedWaiting").value(1))
                .andExpect(jsonPath("$.claimedOverdue").value(1))
                .andExpect(jsonPath("$.shipped").value(1))
                .andExpect(jsonPath("$.done").value(0));

        // 別家機構看到的數量互不影響
        mvc.perform(as(get("/api/organizations/me/wishes/stage-counts"), OTHER_ORG_USER))
                .andExpect(jsonPath("$.all").value(0));
    }

    @Test
    @DisplayName("抽屜：目前認領的完整資料、過往被取消的認領紀錄仍然留著；別家機構 404")
    void detailKeepsPastClaims() throws Exception {
        UUID wishId = open("有過往紀錄");
        UUID first = claim(wishId);
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", first), Map.of("reason", "臨時有事")), claimant.get(first)))
                .andExpect(status().isOk());
        UUID second = claim(wishId);

        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wish.stage").value("CLAIMED_WAITING"))
                .andExpect(jsonPath("$.activeClaim.id").value(second.toString()))
                .andExpect(jsonPath("$.activeClaim.donorEmail").doesNotExist())
                .andExpect(jsonPath("$.pastClaims.length()").value(1))
                .andExpect(jsonPath("$.pastClaims[0].claimId").value(first.toString()))
                .andExpect(jsonPath("$.pastClaims[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.pastClaims[0].reason").value("臨時有事"))
                .andExpect(jsonPath("$.pastClaims[0].donorName").value("熱心民眾"));

        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", UUID.randomUUID()), ORG_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("還沒有人認領的願望沒有認領摘要；草稿也能看抽屜")
    void wishesWithoutClaimHaveNoSummary() throws Exception {
        UUID draftId = draft("沒有認領");

        mvc.perform(as(get("/api/organizations/me/wishes/{id}", draftId), ORG_USER))
                .andExpect(jsonPath("$.wish.stage").value("DRAFT"))
                .andExpect(jsonPath("$.activeClaim").doesNotExist())
                .andExpect(jsonPath("$.pastClaims.length()").value(0));
    }
}
