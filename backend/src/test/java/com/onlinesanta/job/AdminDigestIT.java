package com.onlinesanta.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.onlinesanta.job.dto.AdminDigestResult;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.RecordingMailSender;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DisplayName("管理員每日待審摘要")
class AdminDigestIT extends ApiIntegrationTest {

    @Autowired
    AdminDigestService digest;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    JavaMailSender mailSender;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager entityManager;

    private RecordingMailSender fakeMailSender;

    @BeforeEach
    void setUp() {
        fakeMailSender = (RecordingMailSender) mailSender;
        fakeMailSender.reset();
    }

    private User admin(String email, boolean verified, boolean disabled) {
        User admin = User.newAdmin(email, "管理員");
        if (verified) {
            admin.markEmailVerified(Instant.now());
        } else {
            admin.markEmailUnverified();
        }
        users.save(admin);
        if (disabled) {
            entityManager.flush();
            jdbc.update("UPDATE users SET disabled = true WHERE id = ?", admin.getId());
        }
        return admin;
    }

    private Organization pendingOrganization(String name) {
        return organizations.save(Organization.register(name, "王承辦", name + "@example.org", null, null, null));
    }

    private void pendingWish(Organization approvedOrg, String title) {
        Wish wish = Wish.draft(approvedOrg, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述",
                WishCategory.ART, PriceRange.UNDER_500);
        wish.submitForReview();
        wishes.save(wish);
    }

    private Organization approvedOrganization(String name) {
        Organization org = Organization.register(name, "王承辦", name + "@example.org", null, null, null);
        org.approve(null, "測試");
        return organizations.save(org);
    }

    @Test
    @DisplayName("沒有任何待審項目：不寄信、不記已寄紀錄")
    void nothingPendingSendsNothing() {
        admin("digest-admin@example.org", true, false);

        AdminDigestResult result = digest.send();

        assertThat(result.totalPending()).isZero();
        assertThat(result.sent()).isZero();
        assertThat(fakeMailSender.sent()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_digest_log", Integer.class)).isZero();
    }

    @Test
    @DisplayName("多位管理員各收一封相同的摘要；停用、信箱未驗證的管理員與一般使用者不會收到")
    void everyEligibleAdminGetsOneMail() {
        admin("digest-a@example.org", true, false);
        admin("digest-b@example.org", true, false);
        admin("digest-unverified@example.org", false, false);
        admin("digest-disabled@example.org", true, true);
        users.save(User.newDonor("digest-donor@example.org", "認領者"));
        pendingOrganization("待審機構甲");
        pendingWish(approvedOrganization("已核准機構"), "待審的願望");
        entityManager.flush();

        AdminDigestResult result = digest.send();

        assertThat(result.totalPending()).isEqualTo(2);
        assertThat(result.recipients()).isEqualTo(2);
        assertThat(result.sent()).isEqualTo(2);
        assertThat(fakeMailSender.sent()).extracting(m -> m.getTo()[0])
                .containsExactlyInAnyOrder("digest-a@example.org", "digest-b@example.org");
        SimpleMailMessage mail = fakeMailSender.sent().get(0);
        assertThat(mail.getSubject()).contains("今日待審").contains("機構 1 件").contains("願望 1 件")
                .contains("解除凍結 0 件").contains("解除停權 0 件");
        assertThat(mail.getText()).contains("共有 2 件待審")
                .contains("/admin/orgs?filter=pending").contains("/admin/wishes?filter=review");
        // 沒有待審的兩類，最久等候顯示「—」
        assertThat(mail.getText()).contains("0 件");
        // 內容相同、且每封只寫給一個人（不洩漏其他管理員的信箱）
        assertThat(fakeMailSender.sent().get(1).getText()).isEqualTo(mail.getText());
        assertThat(mail.getText()).doesNotContain("digest-b@example.org").doesNotContain("digest-a@example.org");
    }

    @Test
    @DisplayName("同一天重複觸發不會再寄；隔天（台灣日期）會再寄")
    void sameDayRunsAreDeduplicatedPerAdmin() {
        admin("digest-once@example.org", true, false);
        pendingOrganization("待審機構乙");
        entityManager.flush();
        Instant noon = Instant.parse("2026-12-08T04:00:00Z");   // 台灣 12:00

        assertThat(digest.send(noon).sent()).isEqualTo(1);
        AdminDigestResult again = digest.send(noon.plus(Duration.ofHours(3)));
        assertThat(again.sent()).isZero();
        assertThat(again.alreadySent()).isEqualTo(1);
        assertThat(fakeMailSender.sent()).hasSize(1);

        // 台灣日期過了午夜（UTC 16:00 = 台灣 00:00）算新的一天
        assertThat(digest.send(Instant.parse("2026-12-08T16:30:00Z")).sent()).isEqualTo(1);
        assertThat(fakeMailSender.sent()).hasSize(2);
    }

    @Test
    @DisplayName("有待審項目但沒有符合條件的管理員：不寄、不報錯")
    void noEligibleAdminSendsNothing() {
        pendingOrganization("待審機構丙");
        entityManager.flush();

        AdminDigestResult result = digest.send();

        assertThat(result.totalPending()).isEqualTo(1);
        assertThat(result.recipients()).isZero();
        assertThat(fakeMailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("最久等候以本輪送審時間計算：補件重新送審後從重新送審起算")
    void waitStartsFromTheLatestSubmission() {
        admin("digest-wait@example.org", true, false);
        Organization org = pendingOrganization("等候中的機構");
        entityManager.flush();
        jdbc.update("UPDATE organizations SET submitted_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-12-06T04:00:00Z")), org.getId());

        digest.send(Instant.parse("2026-12-08T10:00:00Z"));    // 2 天 6 小時後

        assertThat(fakeMailSender.sent()).singleElement().satisfies(mail ->
                assertThat(mail.getText()).contains("2 天 6 小時"));
    }
}
