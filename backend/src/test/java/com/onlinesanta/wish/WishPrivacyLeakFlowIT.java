package com.onlinesanta.wish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * 情境一：願望資訊洩漏個資，且已認領、未寄出。
 * 管理員直接修改並凍結 → 機構看到原因、修改後申請解除 → 管理員核准或退回 → 認領者依新版資訊繼續。
 */
@DisplayName("個資外洩處置（修改並凍結、申請解除）")
class WishPrivacyLeakFlowIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "leak-org@example.org";
    private static final String OTHER_ORG_USER = "leak-other-org@example.org";
    private static final String DONOR = "leak-donor@example.com";
    private static final String LEAKED = "王小明在大安國小";

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired ClaimRepository claimRepository;
    @Autowired AdminAuditLogRepository auditLogs;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("外洩測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);

        Organization other = Organization.register("另一間機構", "李承辦", "other@example.org", null, null, null);
        other.approve(null, "測試資料");
        organizations.save(other);
        User otherMember = User.newDonor(TestJwtSupport.uidFor(OTHER_ORG_USER), OTHER_ORG_USER, OTHER_ORG_USER);
        otherMember.joinOrganization(other.getId());
        users.save(otherMember);

        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    /** 已認領、還沒寄出的願望；暱稱洩漏了個資，歷程裡也有含洩漏內容的快照。 */
    private UUID leakedClaimedWish() throws Exception {
        Wish wish = Wish.draft(organization, LEAKED, AgeRange.AGE_7_9, "畫畫",
                "色鉛筆組", "想要畫畫", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        UUID wishId = wishes.saveAndFlush(wish).getId();
        jdbc.update("""
                insert into wish_events (wish_id, event_type, note, snapshot)
                values (?, 'APPROVED', null, ?)""",
                wishId, "{\"title\":\"色鉛筆組\",\"childAlias\":\"" + LEAKED
                        + "\",\"ageRange\":\"AGE_7_9\",\"description\":\"想要畫畫\",\"letterIds\":[]}");
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private UUID wishIdOf(UUID claimId) {
        return jdbc.queryForObject("select wish_id from claims where id = ?", UUID.class, claimId);
    }

    private void adminEdit(UUID wishId, Map<String, Object> body) throws Exception {
        mvc.perform(as(withBody(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/wishes/{id}/content", wishId), body), ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("管理員修改並凍結：內容改掉、禮物不變、歷史快照被遮蔽、稽核不記內容、認領者看到新版與暫停")
    void adminEditsAndFreezes() throws Exception {
        UUID claimId = leakedClaimedWish();
        UUID wishId = wishIdOf(claimId);
        em.flush();

        adminEdit(wishId, Map.of("childAlias", "小星", "reason", "暱稱含真實姓名與學校"));
        em.flush();
        em.clear();

        Wish wish = wishes.findById(wishId).orElseThrow();
        assertThat(wish.getChildAlias()).isEqualTo("小星");
        assertThat(wish.getTitle()).isEqualTo("色鉛筆組");
        assertThat(wish.isFrozen()).isTrue();
        assertThat(wish.getFrozenCause()).isEqualTo(WishFreezeCause.ADMIN_CONTENT);

        // 歷史快照中被改的欄位被遮蔽，其他欄位保留；快照裡不再有外洩的暱稱
        String snapshot = jdbc.queryForObject(
                "select snapshot from wish_events where wish_id = ? and event_type = 'APPROVED'", String.class, wishId);
        assertThat(snapshot).doesNotContain(LEAKED).contains("[已遮蔽]").contains("色鉛筆組");
        assertThat(jdbc.queryForObject(
                "select redacted_at from wish_events where wish_id = ? and event_type = 'APPROVED'",
                java.sql.Timestamp.class, wishId)).isNotNull();

        // 稽核與歷程只記欄位名稱與原因，不記內容
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.EDIT_WISH_CONTENT);
            assertThat(log.getDetail()).contains("childAlias").doesNotContain(LEAKED).doesNotContain("小星");
        });

        // 認領者看到的是新版，且暫停處理中
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.childAlias").value("小星"))
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.pauseReason").value("WISH_FROZEN"));
        // 機構看到凍結原因，可以修改並申請解除
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(jsonPath("$.wish.frozen").value(true))
                .andExpect(jsonPath("$.wish.freezeCause").value("ADMIN_CONTENT"))
                .andExpect(jsonPath("$.wish.frozenReason").value("暱稱含真實姓名與學校"))
                .andExpect(jsonPath("$.canRequestUnlock").value(true))
                .andExpect(jsonPath("$.canReviseContent").value(true));
    }

    @Test
    @DisplayName("機構修改後申請解除：線上內容不變直到核准；核准後內容更新、解除凍結、認領者可繼續")
    void organizationRequestsUnlockAndAdminApproves() throws Exception {
        UUID claimId = leakedClaimedWish();
        UUID wishId = wishIdOf(claimId);
        em.flush();
        adminEdit(wishId, Map.of("childAlias", "小星", "reason", "暱稱含真實姓名與學校"));

        mvc.perform(as(withBody(post("/api/organizations/me/wishes/{id}/unlock-request", wishId),
                        Map.of("message", "已改成化名", "revision", Map.of("childAlias", "星星", "description", "想畫畫"))),
                        ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        // 同時只能有一筆待審
        mvc.perform(as(withBody(post("/api/organizations/me/wishes/{id}/unlock-request", wishId),
                        Map.of("message", "再來一次")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UNLOCK_ALREADY_REQUESTED"));

        // 核准前：認領者看到的仍是管理員處理過的版本，機構的修改不會提早上線
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.childAlias").value("小星"));
        // 管理員抽屜看得到待審申請與舊→新差異
        mvc.perform(as(get("/api/admin/wishes/{id}", wishId), ADMIN))
                .andExpect(jsonPath("$.unlockRequest.status").value("PENDING"))
                .andExpect(jsonPath("$.unlockChanges[?(@.field=='孩子的暱稱')].after").value("星星"))
                .andExpect(jsonPath("$.frozen.cause").value("ADMIN_CONTENT"));
        mvc.perform(as(get("/api/admin/wishes?filter=FROZEN"), ADMIN))
                .andExpect(jsonPath("$.content[0].unlockRequested").value(true));

        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/unlock-request/approve", wishId),
                        Map.of("note", "確認已去識別化")), ADMIN))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        Wish wish = wishes.findById(wishId).orElseThrow();
        assertThat(wish.getChildAlias()).isEqualTo("星星");
        assertThat(wish.getDescription()).isEqualTo("想畫畫");
        assertThat(wish.isFrozen()).isFalse();
        assertThat(claimRepository.findById(claimId).orElseThrow().getDeadlinePausedAt()).isNull();
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.childAlias").value("星星"))
                .andExpect(jsonPath("$.paused").value(false));
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(jsonPath("$.unlockRequest.status").value("APPROVED"))
                .andExpect(jsonPath("$.wish.frozen").value(false));
        assertThat(auditLogs.findAll())
                .anySatisfy(log -> assertThat(log.getAction()).isEqualTo(AdminAuditAction.DECIDE_UNLOCK_REQUEST));
    }

    @Test
    @DisplayName("管理員退回：維持凍結，機構看得到原因並可再申請；機構可以撤回待審的申請")
    void rejectKeepsFrozenAndOrganizationCanWithdraw() throws Exception {
        UUID claimId = leakedClaimedWish();
        UUID wishId = wishIdOf(claimId);
        em.flush();
        adminEdit(wishId, Map.of("childAlias", "小星", "reason", "暱稱含真實姓名與學校"));
        String path = "/api/organizations/me/wishes/{id}/unlock-request";

        mvc.perform(as(withBody(post(path, wishId), Map.of("message", "請解除")), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/unlock-request/reject", wishId),
                        Map.of("note", "說明欄位仍含學校名稱")), ADMIN))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(jsonPath("$.unlockRequest.status").value("REJECTED"))
                .andExpect(jsonPath("$.unlockRequest.decisionNote").value("說明欄位仍含學校名稱"))
                .andExpect(jsonPath("$.wish.frozen").value(true))
                .andExpect(jsonPath("$.canRequestUnlock").value(true));

        // 再申請一次，然後撤回
        mvc.perform(as(withBody(post(path, wishId), Map.of("message", "已修正")), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(post(path + "/withdraw", wishId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mvc.perform(as(post(path + "/withdraw", wishId), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_PENDING_UNLOCK_REQUEST"));
    }

    @Test
    @DisplayName("申請的限制：沒凍結不能申請、別間機構不行、非內容疑慮的凍結不能附修改、停權連動的凍結不能在願望上申請")
    void unlockRequestGuards() throws Exception {
        UUID claimId = leakedClaimedWish();
        UUID wishId = wishIdOf(claimId);
        String path = "/api/organizations/me/wishes/{id}/unlock-request";

        mvc.perform(as(withBody(post(path, wishId), Map.of("message", "x")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_UNLOCKABLE"));

        em.flush();
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId), Map.of("note", "單純凍結，先暫停")), ADMIN))
                .andExpect(status().isOk());
        mvc.perform(as(withBody(post(path, wishId),
                        Map.of("message", "x", "revision", Map.of("childAlias", "新暱稱"))), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REVISION_NOT_ALLOWED"));
        mvc.perform(as(withBody(post(path, wishId), Map.of("message", "x")), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
        // 非內容疑慮的凍結可以不改內容直接申請
        mvc.perform(as(withBody(post(path, wishId), Map.of("message", "認為是誤判")), ORG_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("遮蔽對話訊息：雙方看到已遮蔽，管理員看得到原文與遮蔽旗標，稽核不記內容")
    void adminRedactsAMessage() throws Exception {
        UUID claimId = leakedClaimedWish();
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId), Map.of("body", "我是王小明，住在大安路")), DONOR))
                .andExpect(status().isOk());
        em.flush();
        Long messageId = jdbc.queryForObject(
                "select id from messages where claim_id = ? and kind = 'USER'", Long.class, claimId);

        mvc.perform(as(withBody(post("/api/admin/claims/{id}/messages/{mid}/redact", claimId, messageId),
                        Map.of("note", "含個人住址")), ADMIN))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mvc.perform(as(get("/api/claims/{id}/messages", claimId), ORG_USER))
                .andExpect(jsonPath("$[?(@.kind=='USER')].body").value("此訊息已被管理員遮蔽"));
        mvc.perform(as(get("/api/claims/{id}/messages", claimId), DONOR))
                .andExpect(jsonPath("$[?(@.kind=='USER')].body").value("此訊息已被管理員遮蔽"));
        mvc.perform(as(get("/api/admin/claims/{id}/messages", claimId), ADMIN))
                .andExpect(jsonPath("$[?(@.redacted==true)].body").value("我是王小明，住在大安路"));
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.REDACT_MESSAGE);
            assertThat(log.getDetail()).contains("含個人住址").doesNotContain("王小明");
        });
    }
}
