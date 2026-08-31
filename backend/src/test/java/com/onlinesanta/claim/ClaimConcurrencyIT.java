package com.onlinesanta.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.PostgresIntegrationTest;
import com.onlinesanta.support.TestSecurityConfig;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

/**
 * 上架首日的搶領情境：大量民眾同時點下同一個願望的認領。
 *
 * <p>本測試刻意<strong>不加</strong> {@code @Transactional}——測試若被包在單一交易裡，
 * 各執行緒就會共用同一個連線與交易，那正是要驗證的併發控制完全不會被觸發。
 * 因此改為手動清理資料。
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@DisplayName("搶領的併發控制")
class ClaimConcurrencyIT extends PostgresIntegrationTest {

    private static final int CONTENDERS = 50;

    @Autowired
    MockMvc mvc;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    JdbcTemplate jdbc;

    private UUID wishId;
    private List<String> donorEmails;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        Organization organization = Organization.register(
                "搶領測試之家", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);

        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                "唯一的一份禮物", "只有一個人領得到", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        wishId = wishes.save(wish).getId();

        // 事先建好帳號，讓測試專注在認領的競爭上，而不是帳號建立的競爭
        donorEmails = java.util.stream.IntStream.range(0, CONTENDERS)
                .mapToObj(i -> "donor%02d@example.com".formatted(i))
                .toList();
        donorEmails.forEach(email ->
                users.save(User.newDonor(TestJwtSupport.uidFor(email), email, email)));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        jdbc.execute("DELETE FROM claim_events");
        jdbc.execute("DELETE FROM claims");
        jdbc.execute("DELETE FROM wishes");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("DELETE FROM organizations");
    }

    @Test
    @DisplayName("50 人同時搶同一個願望，只有一個人領到")
    void exactlyOneDonorWinsTheRace() throws Exception {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        // 所有執行緒先卡在同一個閘門上，開閘後才幾乎同時送出請求
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS)) {
            List<Future<Integer>> results = donorEmails.stream()
                    .map(email -> pool.submit((Callable<Integer>) () -> {
                        startGate.await();
                        return mvc.perform(post("/api/wishes/{id}/claim", wishId)
                                        .header("Authorization", "Bearer " + TestJwtSupport.tokenFor(email)))
                                .andReturn().getResponse().getStatus();
                    }))
                    .toList();

            startGate.countDown();

            for (Future<Integer> result : results) {
                int status = result.get(30, TimeUnit.SECONDS);
                switch (status) {
                    case 201 -> created.incrementAndGet();
                    case 409 -> conflicted.incrementAndGet();
                    default -> unexpected.incrementAndGet();
                }
            }
        }

        assertThat(unexpected.get())
                .as("除了 201 與 409 之外不該出現其他回應——特別是 500，"
                        + "那代表併發衝突洩漏成了資料庫例外而非乾淨的業務錯誤")
                .isZero();
        assertThat(created.get()).as("恰好一人認領成功").isOne();
        assertThat(conflicted.get()).as("其餘皆收到 409").isEqualTo(CONTENDERS - 1);
    }

    @Test
    @DisplayName("競爭結束後資料庫只留下一筆有效認領")
    void databaseHoldsExactlyOneActiveClaim() throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS)) {
            List<Future<Integer>> results = donorEmails.stream()
                    .map(email -> pool.submit((Callable<Integer>) () -> {
                        startGate.await();
                        return mvc.perform(post("/api/wishes/{id}/claim", wishId)
                                        .header("Authorization", "Bearer " + TestJwtSupport.tokenFor(email)))
                                .andReturn().getResponse().getStatus();
                    }))
                    .toList();

            startGate.countDown();
            for (Future<Integer> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        }

        Integer activeClaims = jdbc.queryForObject(
                "SELECT count(*) FROM claims WHERE wish_id = ? "
                        + "AND status IN ('CLAIMED','SHIPPED','RECEIVED','COMPLETED')",
                Integer.class, wishId);
        assertThat(activeClaims).as("uq_active_claim_per_wish 保證的不變條件").isOne();

        String wishStatus = jdbc.queryForObject(
                "SELECT status FROM wishes WHERE id = ?", String.class, wishId);
        assertThat(wishStatus).isEqualTo("CLAIMED");

        Long version = jdbc.queryForObject(
                "SELECT version FROM wishes WHERE id = ?", Long.class, wishId);
        assertThat(version)
                .as("版本號只被成功的那一次條件式 UPDATE 遞增，失敗的 49 次不留痕跡")
                .isEqualTo(1L);

        Integer claimEvents = jdbc.queryForObject(
                "SELECT count(*) FROM claim_events WHERE event_type = 'CLAIMED'", Integer.class);
        assertThat(claimEvents).as("稽核軌跡也只該有一筆").isOne();
    }

    @Test
    @DisplayName("資料庫層的部分唯一索引擋得住繞過服務層的寫入")
    void partialUniqueIndexRejectsASecondActiveClaimWrittenDirectly() throws Exception {
        mvc.perform(post("/api/wishes/{id}/claim", wishId)
                        .header("Authorization", "Bearer " + TestJwtSupport.tokenFor(donorEmails.get(0))))
                .andReturn();

        UUID otherDonor = users.findByEmailIgnoreCase(donorEmails.get(1)).orElseThrow().getId();

        // 模擬「日後有人寫了繞過 ClaimService 的程式碼」——資料庫仍必須守住
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("""
                INSERT INTO claims (id, wish_id, donor_user_id, status,
                                    release_policy_snapshot, claimed_at)
                VALUES (gen_random_uuid(), ?, ?, 'CLAIMED', 'MANUAL', now())
                """, wishId, otherDonor)))
                .as("uq_active_claim_per_wish 必須拒絕第二筆有效認領")
                .isNotNull()
                .hasMessageContaining("uq_active_claim_per_wish");
    }
}
