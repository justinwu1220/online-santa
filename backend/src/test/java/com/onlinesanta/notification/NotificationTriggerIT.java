package com.onlinesanta.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.transaction.TestTransaction;

import com.onlinesanta.admin.dto.ReviewDecisionRequest;
import com.onlinesanta.admin.dto.ReviewReasonRequest;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.claim.dto.ShipRequest;
import com.onlinesanta.event.ClaimCreatedEvent;
import com.onlinesanta.event.FeedbackPhotoConfirmedEvent;
import com.onlinesanta.event.NewMessageEvent;
import com.onlinesanta.event.OrganizationReviewedEvent;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.InMemoryObjectStorage;
import com.onlinesanta.support.RecordingMailSender;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

/**
 * Email 通知的五種觸發時機。
 *
 * <p>分成三類驗證，各自對應不同的可測性考量：
 *
 * <ul>
 *   <li><b>事件是否正確發布</b>——用 {@link ApplicationEvents} 直接斷言，不依賴交易
 *       真的 commit（{@code ApiIntegrationTest} 整個測試方法包在一個交易裡，方法結束
 *       會自動回滾，{@code @TransactionalEventListener(AFTER_COMMIT)} 因此完全不會
 *       觸發——事件有沒有「發布」跟它「最終有沒有被消費」是兩件事，這裡只驗前者）。
 *   <li><b>監聽器組出的信件內容是否正確</b>——直接呼叫監聽器方法（不經過真正的
 *       AFTER_COMMIT 派送），因為這裡要驗的是「給定一個事件，組出的收件人與內文對不
 *       對」，不是交易時機本身。
 *   <li><b>AFTER_COMMIT 語意本身</b>（交易 commit 才寄信、回滾不寄信）——用
 *       {@link TestTransaction} 強制真正 commit 一次，驗證機制本身，測完手動清掉
 *       建立的資料，不依賴 {@code ApiIntegrationTest} 的自動回滾。
 * </ul>
 */
@RecordApplicationEvents
@DisplayName("Email 通知的觸發時機")
class NotificationTriggerIT extends ApiIntegrationTest {

    private static final String ORG_NAME = "通知測試機構";
    private static final String ORG_USER = "notify-org@example.org";
    private static final String OTHER_ORG_USER = "notify-org-2@example.org";
    private static final String DONOR = "notify-donor@example.com";
    private static final String ADMIN = "platform-admin@example.com";
    private static final String JPEG = "image/jpeg";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    ObjectStorage storage;

    @Autowired
    JavaMailSender mailSender;

    @Autowired
    ApplicationEvents events;

    @Autowired
    ClaimCreatedNotificationListener claimCreatedListener;

    @Autowired
    OrganizationReviewedNotificationListener organizationReviewedListener;

    @Autowired
    FeedbackPhotoConfirmedNotificationListener feedbackPhotoListener;

    @Autowired
    NewMessageNotificationListener newMessageListener;

    private RecordingMailSender fakeMailSender;
    private Organization organization;

    @BeforeEach
    void setUp() {
        fakeMailSender = (RecordingMailSender) mailSender;
        fakeMailSender.reset();

        organization = approvedOrganization("通知測試機構", "org-contact@example.org", ORG_USER);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * 只有真的用 {@link TestTransaction} commit 過的測試才需要呼叫這個方法清理——
     * 一般測試的資料本來就會隨 {@code @Transactional} 測試基底自動回滾，不需要、
     * 也不能在這裡順便 commit 一次清理，否則反而讓「一般測試」的資料也被留下來。
     *
     * <p>清理本身也得明確 commit：呼叫時當下的交易是 {@link TestTransaction#start()}
     * 開的新交易，若不主動 flagForCommit，一樣會被測試框架在方法結束時整個回滾，
     * 清理等於沒做。
     */
    private void commitCleanupOfSetUpData() {
        TestTransaction.start();
        jdbc.update("""
                DELETE FROM messages WHERE claim_id IN (
                    SELECT c.id FROM claims c
                    JOIN wishes w ON w.id = c.wish_id
                    JOIN organizations o ON o.id = w.organization_id
                    WHERE o.name = ?)
                """, ORG_NAME);
        jdbc.update("""
                DELETE FROM claim_events WHERE claim_id IN (
                    SELECT c.id FROM claims c
                    JOIN wishes w ON w.id = c.wish_id
                    JOIN organizations o ON o.id = w.organization_id
                    WHERE o.name = ?)
                """, ORG_NAME);
        jdbc.update("""
                DELETE FROM claims WHERE wish_id IN (
                    SELECT id FROM wishes WHERE organization_id = (
                        SELECT id FROM organizations WHERE name = ?))
                """, ORG_NAME);
        jdbc.update("""
                DELETE FROM wishes WHERE organization_id = (
                    SELECT id FROM organizations WHERE name = ?)
                """, ORG_NAME);
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", ORG_USER, DONOR);
        jdbc.update("DELETE FROM organizations WHERE name = ?", ORG_NAME);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 留一個 active 交易給測試框架收尾（它會 rollback 這個空交易，無妨）
        TestTransaction.start();
    }

    private Organization approvedOrganization(String name, String contactEmail, String memberEmail) {
        Organization org = Organization.register(name, "王承辦", contactEmail, null, null, null);
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

    private UUID claimAs(UUID wishId, String donorEmail) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donorEmail))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

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

    // ================================================================ 事件確實被發布

    @Test
    @DisplayName("認領成立會發布 ClaimCreatedEvent")
    void claimCreationPublishesEvent() throws Exception {
        UUID wishId = publishedWish("會被認領");
        UUID claimId = claimAs(wishId, DONOR);

        assertThat(events.stream(ClaimCreatedEvent.class))
                .singleElement()
                .satisfies(e -> assertThat(e.claimId()).isEqualTo(claimId));
    }

    @Test
    @DisplayName("核准與駁回都會發布 OrganizationReviewedEvent，approved 欄位正確")
    void organizationReviewPublishesEvent() throws Exception {
        Organization pending = Organization.register(
                "待審機構", "王承辦", "pending@example.org", null, null, null);
        organizations.save(pending);

        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/approve", pending.getId()),
                        new ReviewDecisionRequest("證件齊全")), ADMIN))
                .andExpect(status().isOk());

        Organization pending2 = Organization.register(
                "待審機構二", "王承辦", "pending2@example.org", null, null, null);
        organizations.save(pending2);
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/reject", pending2.getId()),
                        new ReviewReasonRequest("請補立案證明")), ADMIN))
                .andExpect(status().isOk());

        assertThat(events.stream(OrganizationReviewedEvent.class))
                .anySatisfy(e -> {
                    assertThat(e.organizationId()).isEqualTo(pending.getId());
                    assertThat(e.approved()).isTrue();
                })
                .anySatisfy(e -> {
                    assertThat(e.organizationId()).isEqualTo(pending2.getId());
                    assertThat(e.approved()).isFalse();
                });
    }

    @Test
    @DisplayName("回饋照片確認上傳會發布 FeedbackPhotoConfirmedEvent，其他附件用途不會")
    void feedbackPhotoConfirmationPublishesEvent() throws Exception {
        // 願望示意圖是另一種用途，不該觸發回饋照片的通知事件
        Wish draft = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                "有示意圖的願望", "描述", WishCategory.ART, PriceRange.UNDER_500);
        UUID draftWishId = wishes.save(draft).getId();
        uploadAndConfirm(AttachmentPurpose.WISH_IMAGE, draftWishId, ORG_USER);
        assertThat(events.stream(FeedbackPhotoConfirmedEvent.class)).isEmpty();

        UUID wishId = publishedWish("回饋照片");
        UUID claimId = claimAs(wishId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());

        uploadAndConfirm(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        assertThat(events.stream(FeedbackPhotoConfirmedEvent.class))
                .singleElement()
                .satisfies(e -> assertThat(e.claimId()).isEqualTo(claimId));
    }

    private UUID uploadAndConfirm(AttachmentPurpose purpose, UUID targetId, String userEmail) throws Exception {
        var request = new UploadUrlRequest(purpose, targetId, JPEG, 1_000);
        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), userEmail))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String uploadUrl = node.get("uploadUrl").asText();
        String objectName = uploadUrl.substring(
                uploadUrl.indexOf(purpose.prefix()), uploadUrl.indexOf('?'));

        ((InMemoryObjectStorage) storage).simulateUpload(purpose.bucket(), objectName, JPEG, 1_000);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), userEmail))
                .andExpect(status().isOk());
        return attachmentId;
    }

    // ================================================================ 防轟炸節流（新訊息）

    @Test
    @DisplayName("對方已有未讀訊息時，同一寄件人再寄不會觸發通知；對方已讀後才會再觸發")
    void newMessageThrottleSkipsWhileRecipientHasUnread() throws Exception {
        UUID claimId = claimAs(publishedWish("訊息節流"), DONOR);

        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId),
                        new SendMessageRequest("第一則")), DONOR))
                .andExpect(status().isOk());
        assertThat(events.stream(NewMessageEvent.class)).hasSize(1);

        // 對方（機構）還沒讀第一則，捐贈者又寄第二則——不該再觸發
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId),
                        new SendMessageRequest("第二則，對方還沒讀第一則")), DONOR))
                .andExpect(status().isOk());
        assertThat(events.stream(NewMessageEvent.class)).hasSize(1);

        // 機構讀取（markRead）之後，捐贈者再寄一則，應該恢復觸發
        mvc.perform(as(post("/api/claims/{id}/messages/mark-read", claimId), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId),
                        new SendMessageRequest("第三則，對方已讀前面的")), DONOR))
                .andExpect(status().isOk());
        assertThat(events.stream(NewMessageEvent.class)).hasSize(2);
    }

    // ================================================================ 監聽器組出的信件內容

    @Test
    @DisplayName("認領成立通知寄給機構的 contactEmail，內文含願望標題與連結")
    void claimCreatedNotificationContent() throws Exception {
        UUID wishId = publishedWish("內容檢查的願望");
        UUID claimId = claimAs(wishId, DONOR);

        claimCreatedListener.onClaimCreated(new ClaimCreatedEvent(claimId));
        awaitMailCount(1);

        assertThat(fakeMailSender.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.getTo()).containsExactly("org-contact@example.org");
            assertThat(mail.getSubject()).contains("內容檢查的願望");
            assertThat(mail.getText()).contains("內容檢查的願望").contains("/org/claims");
        });
    }

    @Test
    @DisplayName("機構審核通過與駁回的信件內容不同，駁回會附上理由")
    void organizationReviewedNotificationContent() throws Exception {
        organization.reject(null, "請補立案證明文件");
        organizations.save(organization);

        organizationReviewedListener.onOrganizationReviewed(
                new OrganizationReviewedEvent(organization.getId(), false));
        awaitMailCount(1);

        assertThat(fakeMailSender.sent()).singleElement().satisfies((SimpleMailMessage mail) -> {
            assertThat(mail.getTo()).containsExactly("org-contact@example.org");
            assertThat(mail.getSubject()).contains("需要補件");
            assertThat(mail.getText()).contains("請補立案證明文件");
        });
    }

    @Test
    @DisplayName("回饋照片通知寄給捐贈者，內文連到我的認領詳情頁")
    void feedbackPhotoNotificationContent() throws Exception {
        UUID claimId = claimAs(publishedWish("回饋內容檢查"), DONOR);

        feedbackPhotoListener.onFeedbackPhotoConfirmed(new FeedbackPhotoConfirmedEvent(claimId));
        awaitMailCount(1);

        assertThat(fakeMailSender.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.getTo()).containsExactly(DONOR);
            assertThat(mail.getText()).contains("/me/claims/" + claimId);
        });
    }

    @Test
    @DisplayName("新訊息通知：捐贈者發送時通知機構，機構發送時通知捐贈者")
    void newMessageNotificationRecipientDependsOnSender() throws Exception {
        UUID claimId = claimAs(publishedWish("訊息內容檢查"), DONOR);
        User donor = users.findAll().stream()
                .filter(u -> u.getEmail().equals(DONOR)).findFirst().orElseThrow();

        newMessageListener.onNewMessage(new NewMessageEvent(claimId, donor.getId()));
        awaitMailCount(1);
        assertThat(fakeMailSender.sent()).singleElement()
                .satisfies(mail -> assertThat(mail.getTo()).containsExactly("org-contact@example.org"));

        User orgMember = users.findAll().stream()
                .filter(u -> u.getEmail().equals(ORG_USER)).findFirst().orElseThrow();
        newMessageListener.onNewMessage(new NewMessageEvent(claimId, orgMember.getId()));
        awaitMailCount(2);
        assertThat(fakeMailSender.sent().get(1).getTo()).containsExactly(DONOR);
    }

    // ================================================================ AFTER_COMMIT 語意本身

    @Test
    @DisplayName("交易真正 commit 之後，通知信才會被寄出")
    void notificationIsSentOnlyAfterCommit() throws Exception {
        UUID wishId = publishedWish("commit 後才寄信");
        claimAs(wishId, DONOR);

        // 認領已經送出，但這個測試方法目前還在同一個交易裡，尚未 commit
        assertThat(fakeMailSender.sent()).isEmpty();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        awaitMailCount(1);
        assertThat(fakeMailSender.sent()).singleElement()
                .satisfies(mail -> assertThat(mail.getTo()).containsExactly("org-contact@example.org"));

        // 這次真正 commit 過，資料不會隨測試自動回滾，明確清掉並真正 commit 這次清理
        commitCleanupOfSetUpData();
    }

    @Test
    @DisplayName("交易回滾時，不會寄出通知信")
    void notificationIsNotSentWhenTransactionRollsBack() throws Exception {
        UUID wishId = publishedWish("回滾不寄信");
        claimAs(wishId, DONOR);

        // 明確標記回滾（而非 flagForCommit），驗證回滾路徑上 AFTER_COMMIT 監聽器
        // 完全不會被觸發
        TestTransaction.flagForRollback();
        TestTransaction.end();

        // 給非同步執行緒一點時間，確認「真的沒有」而不是「還沒輪到」
        Thread.sleep(300);
        assertThat(fakeMailSender.sent()).isEmpty();

        TestTransaction.start();
    }
}
