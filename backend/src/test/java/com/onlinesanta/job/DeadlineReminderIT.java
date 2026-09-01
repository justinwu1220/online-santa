package com.onlinesanta.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.claim.dto.ShipRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.RecordingMailSender;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("寄送期限提醒排程")
class DeadlineReminderIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "deadline-org@example.org";
    private static final String DONOR = "deadline-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    JavaMailSender mailSender;

    @Autowired
    AdminAuditLogRepository auditLogs;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager entityManager;

    private RecordingMailSender fakeMailSender;
    private Organization organization;

    @BeforeEach
    void setUp() {
        fakeMailSender = (RecordingMailSender) mailSender;
        fakeMailSender.reset();

        organization = Organization.register(
                "期限提醒測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);

        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
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

    private void setShipDeadline(UUID claimId, Instant deadline) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                Timestamp.from(deadline), claimId);
        entityManager.clear();
    }

    private Instant deadlineReminderSentAtOf(UUID claimId) {
        entityManager.flush();
        Timestamp sentAt = jdbc.queryForObject(
                "SELECT deadline_reminder_sent_at FROM claims WHERE id = ?",
                Timestamp.class, claimId);
        return sentAt == null ? null : sentAt.toInstant();
    }

    private String sweep() throws Exception {
        return mvc.perform(as(post("/api/admin/jobs/send-deadline-reminders"), ADMIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * 通知信是非同步寄送，{@code sweep()} 的 HTTP 回應回來時信不一定已經進到
     * {@link RecordingMailSender} 裡——這裡等到真的收到才繼續斷言內容。
     */
    private void awaitMailCount(int expected) {
        long deadline = System.currentTimeMillis() + 3000;
        while (fakeMailSender.sent().size() < expected && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ------------------------------------------------------------ 核心行為

    @Test
    @DisplayName("寄送期限在 2 天內的 CLAIMED 認領會收到提醒，且記下已寄送時間")
    void claimsWithinTwoDaysGetReminded() throws Exception {
        UUID claimId = claimAs(publishedWish("快到期的願望"), DONOR);
        setShipDeadline(claimId, Instant.now().plus(1, ChronoUnit.DAYS));

        mvc.perform(as(post("/api/admin/jobs/send-deadline-reminders"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(1))
                .andExpect(jsonPath("$.sent").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        awaitMailCount(1);
        assertThat(fakeMailSender.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.getTo()).containsExactly(DONOR);
            assertThat(mail.getSubject()).contains("快到期的願望");
        });
        assertThat(deadlineReminderSentAtOf(claimId)).isNotNull();
    }

    @Test
    @DisplayName("寄送期限在 2 天以外的認領不會被提醒")
    void claimsOutsideWindowAreNotReminded() throws Exception {
        UUID claimId = claimAs(publishedWish("還早"), DONOR);
        setShipDeadline(claimId, Instant.now().plus(5, ChronoUnit.DAYS));

        mvc.perform(as(post("/api/admin/jobs/send-deadline-reminders"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(0));

        assertThat(fakeMailSender.sent()).isEmpty();
        assertThat(deadlineReminderSentAtOf(claimId)).isNull();
    }

    @Test
    @DisplayName("已經逾期的認領不會被提醒——那是逾期釋回排程的職責")
    void alreadyOverdueClaimsAreNotReminded() throws Exception {
        UUID claimId = claimAs(publishedWish("已經逾期"), DONOR);
        setShipDeadline(claimId, Instant.now().minus(1, ChronoUnit.DAYS));

        mvc.perform(as(post("/api/admin/jobs/send-deadline-reminders"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(0));

        assertThat(fakeMailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("已經寄過提醒的認領不會重複提醒")
    void alreadyRemindedClaimsAreNotRemindedAgain() throws Exception {
        UUID claimId = claimAs(publishedWish("提醒過一次"), DONOR);
        setShipDeadline(claimId, Instant.now().plus(1, ChronoUnit.DAYS));

        sweep();
        awaitMailCount(1);
        assertThat(fakeMailSender.sent()).hasSize(1);

        mvc.perform(as(post("/api/admin/jobs/send-deadline-reminders"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(0));
        assertThat(fakeMailSender.sent()).hasSize(1);
    }

    @Test
    @DisplayName("不是 CLAIMED 狀態的認領不會被提醒，即使寄送期限落在窗口內")
    void nonClaimedStatusIsNotReminded() throws Exception {
        UUID claimId = claimAs(publishedWish("已經寄出了"), DONOR);
        setShipDeadline(claimId, Instant.now().plus(1, ChronoUnit.DAYS));
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());

        mvc.perform(as(post("/api/admin/jobs/send-deadline-reminders"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(0));

        assertThat(fakeMailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("一次能處理多筆，只挑中真的在窗口內的那些")
    void sweepHandlesMultipleClaimsAndOnlyPicksThoseInWindow() throws Exception {
        UUID inWindowA = claimAs(publishedWish("窗口內 A"), DONOR);
        setShipDeadline(inWindowA, Instant.now().plus(6, ChronoUnit.HOURS));

        UUID inWindowB = claimAs(publishedWish("窗口內 B"), DONOR);
        setShipDeadline(inWindowB, Instant.now().plus(47, ChronoUnit.HOURS));

        UUID outsideWindow = claimAs(publishedWish("窗口外"), DONOR);
        setShipDeadline(outsideWindow, Instant.now().plus(10, ChronoUnit.DAYS));

        mvc.perform(as(post("/api/admin/jobs/send-deadline-reminders"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(2))
                .andExpect(jsonPath("$.sent").value(2));

        assertThat(deadlineReminderSentAtOf(inWindowA)).isNotNull();
        assertThat(deadlineReminderSentAtOf(inWindowB)).isNotNull();
        assertThat(deadlineReminderSentAtOf(outsideWindow)).isNull();
    }

    // ------------------------------------------------------------ 稽核與授權

    @Test
    @DisplayName("手動觸發會寫入稽核紀錄")
    void manualTriggerIsAudited() throws Exception {
        UUID claimId = claimAs(publishedWish("稽核檢查"), DONOR);
        setShipDeadline(claimId, Instant.now().plus(1, ChronoUnit.DAYS));

        sweep();

        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(AdminAuditAction.RUN_DEADLINE_REMINDERS);
                    assertThat(log.getTargetId()).isNull();
                    assertThat(log.getDetail()).contains("寄出 1 筆");
                });
    }

    @Test
    @DisplayName("手動觸發限管理員")
    void manualTriggerIsAdminOnly() throws Exception {
        mvc.perform(as(post("/api/admin/jobs/send-deadline-reminders"), ORG_USER))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/jobs/send-deadline-reminders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("排程端點需要 Google OIDC token，Firebase token 無效")
    void internalEndpointRejectsRegularUserTokens() throws Exception {
        mvc.perform(as(post("/internal/jobs/send-deadline-reminders"), ADMIN))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/jobs/send-deadline-reminders"))
                .andExpect(status().isUnauthorized());
    }
}
